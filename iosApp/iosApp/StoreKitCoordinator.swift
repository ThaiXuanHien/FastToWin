import Foundation
import StoreKit
import Shared

/// Owns StoreKit 2 for the shared Kotlin shop. The signed JWS goes to the server first;
/// a transaction is finished only after the server reports the Gem grant.
@MainActor
final class StoreKitCoordinator {
    static let shared = StoreKitCoordinator()

    private var products: [String: Product] = [:]
    private var updatesTask: Task<Void, Never>?

    func start() {
        IosStoreKitBridge.shared.install(
            loadProducts: { ids in Task { @MainActor in await StoreKitCoordinator.shared.loadProducts(ids) } },
            purchase: { productId, token in
                Task { @MainActor in await StoreKitCoordinator.shared.purchase(productId: productId, appAccountToken: token) }
            },
            finish: { transactionId in Task { @MainActor in await StoreKitCoordinator.shared.finish(transactionId: transactionId) } }
        )
        guard updatesTask == nil else { return }
        // Ask to Buy approvals, interrupted purchases, purchases on other devices and refunds arrive here.
        updatesTask = Task.detached(priority: .background) {
            for await result in Transaction.updates {
                await StoreKitCoordinator.shared.handle(result)
            }
        }
    }

    private func loadProducts(_ ids: [String]) async {
        do {
            let loaded = try await Product.products(for: ids)
            products = Dictionary(loaded.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            IosStoreKitBridge.shared.onProductsLoaded(
                productIds: loaded.map(\.id),
                displayPrices: loaded.map(\.displayPrice)
            )
        } catch {
            print("FastToWin: could not load App Store products")
            IosStoreKitBridge.shared.onProductsFailed()
        }
        // Paid but not yet granted (app killed, offline, server unavailable): retry the grant.
        for await result in Transaction.unfinished {
            handle(result)
        }
    }

    private func purchase(productId: String, appAccountToken: String) async {
        guard let product = products[productId], let token = UUID(uuidString: appAccountToken) else {
            IosStoreKitBridge.shared.onPurchaseFailed()
            return
        }
        do {
            switch try await product.purchase(options: [.appAccountToken(token)]) {
            case .success(let verification):
                handle(verification)
            case .pending:
                IosStoreKitBridge.shared.onPurchasePending()
            case .userCancelled:
                IosStoreKitBridge.shared.onPurchaseCancelled()
            @unknown default:
                IosStoreKitBridge.shared.onPurchaseFailed()
            }
        } catch {
            IosStoreKitBridge.shared.onPurchaseFailed()
        }
    }

    private func handle(_ result: VerificationResult<Transaction>) {
        guard case .verified(let transaction) = result else {
            // Never forward or finish a transaction StoreKit could not verify on device.
            IosStoreKitBridge.shared.onPurchaseFailed()
            return
        }
        guard transaction.productType == .consumable else { return }
        if transaction.revocationDate != nil {
            // Refunded or revoked before the grant: nothing to deliver, just clear it from the queue.
            Task { await transaction.finish() }
            return
        }
        IosStoreKitBridge.shared.onTransaction(
            transactionId: String(transaction.id),
            productId: transaction.productID,
            signedTransaction: result.jwsRepresentation,
            appAccountToken: transaction.appAccountToken?.uuidString.lowercased()
        )
    }

    private func finish(transactionId: String) async {
        guard let id = UInt64(transactionId) else {
            IosStoreKitBridge.shared.onFinished(transactionId: transactionId, success: false)
            return
        }
        for await result in Transaction.unfinished {
            if case .verified(let transaction) = result, transaction.id == id {
                await transaction.finish()
                break
            }
        }
        // Not in the unfinished queue means it was already finished.
        IosStoreKitBridge.shared.onFinished(transactionId: transactionId, success: true)
    }
}
