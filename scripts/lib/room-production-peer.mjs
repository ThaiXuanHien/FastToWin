import assert from 'node:assert/strict'

/** Docker releases the address when a container stops; a running guard must reserve it. */
export function replacePrivateWorker({docker,address,launchWorker,worker,retired,launchGuard}) {
    const previous=address('b')
    assert(previous,'Missing original worker address')
    docker(['stop','--time','2',worker])
    docker(['rename',worker,retired])
    launchGuard()
    assert.equal(address('address-guard'),previous,'Old worker address was not reserved')
    launchWorker()
    const replacement=address('b')
    assert(replacement && replacement!==previous,'Worker address was not replaced')
    return {addressChanged:true,previous,replacement}
}