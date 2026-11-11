import { test } from 'node:test'
import assert from 'node:assert/strict'
import { cleanupFixture, railwayCleanupApi } from '../lib/room-railway-cleanup.mjs'

const project = 'b5c5db71-2df8-46da-84fb-d9b9df6d7951'
const environment = '8bcf4119-3a6b-49b3-b4ff-8ea20080f3b9'
const workspace = '7bcffef9-838f-4192-9901-97dc105b8995'
const prefix = 'room-pilot-6eb282c86681'
const old = { id: '2570e9e3-0832-40a5-9982-ca6e595f1e84', name: 'staging-mail' }
const owned = { id: '80ed3ff5-1804-465a-bda4-c05f7efe1228', name: `${prefix}-pg` }
const scope = { project, environment, workspace, phases: [{ prefix, services: [{ name: owned.name }] }] }
const journal = { project, environment, prefix, services: [owned] }
const initial = { project, environment, workspace, services: [old], instances: [old.id], volumes: [], attachments: [], activeCompute: 0 }

test('removes only a journaled ID and verifies a fresh inventory', async () => {
    let snapshots = 0
    const removed = []
    const api = {
        inventory: async () => ++snapshots === 1
            ? { ...initial, services: [old, owned], instances: [old.id, owned.id] } : initial,
        deleteService: async id => { removed.push(id) }
    }
    await cleanupFixture(scope, journal, initial, api)
    assert.deepEqual(removed, [owned.id])
    assert.equal(snapshots, 2)
})

test('refuses an ID substituted under the expected name or an unexpected environment', async () => {
    for (const snapshot of [
        { ...initial, services: [old, { id: 'd6c439f3-0303-40d4-8d16-71a5411888a5', name: owned.name }] },
        { ...initial, environment: project, services: [old, owned] },
        { ...initial, services: [old, owned], instances: [old.id] }
    ]) {
        await assert.rejects(cleanupFixture(scope, journal, initial, {
            inventory: async () => snapshot,
            deleteService: async () => assert.fail('unproven ownership')
        }), /identity|inventory|environment/i)
    }
})

test('allows a cleanup retry and reports an unjournaled fixture instead of deleting by prefix', async () => {
    await cleanupFixture(scope, journal, initial, {
        inventory: async () => initial,
        deleteService: async () => assert.fail('already absent')
    })
    await assert.rejects(cleanupFixture(scope, { ...journal, services: [] }, initial, {
        inventory: async () => ({ ...initial, services: [old, owned], instances: [old.id, owned.id] }),
        deleteService: async () => assert.fail('unrecorded ID')
    }), /inventory/i)
})

test('cannot certify cleanup if a service persists or an old volume changes', async () => {
    const before = { ...initial, services: [old, owned], instances: [old.id, owned.id] }
    for (const after of [before, { ...initial, volumes: [{ id: 'unexpected' }] }, { ...initial, activeCompute: 1 }]) {
        let read = false
        await assert.rejects(cleanupFixture(scope, journal, initial, {
            inventory: async () => { if (read) return after; read = true; return before },
            deleteService: async id => assert.equal(id, owned.id)
        }), /inventory|volume|compute/i)
    }
})

test('an unexpected persistent attachment prevents service deletion', async () => {
    await assert.rejects(cleanupFixture(scope, journal, initial, {
        inventory: async () => ({ ...initial, services: [old, owned], instances: [old.id, owned.id],
            attachments: [{ id: 'unexpected', serviceId: owned.id }] }),
        deleteService: async () => assert.fail('persistent storage attached')
    }), /volume attachments/i)
})

test('a live latest deployment is counted even when activeDeployments is empty', async () => {
    const connection = values => ({ edges: values.map(node => ({ node })) })
    const api = railwayCleanupApi(async query => {
        assert.match(query, /latestDeployment/)
        return { project: { id: project, name: 'FastToWin Staging', workspaceId: workspace,
            services: connection([old]), volumes: connection([]), environments: connection([{
                id: environment, name: 'staging', volumeInstances: connection([]), serviceInstances: connection([{
                    serviceId: old.id, activeDeployments: [], latestDeployment: { id: owned.id, status: 'SUCCESS', deploymentStopped: false }
                }])
            }])
        } }
    }, scope)
    assert.equal((await api.inventory()).activeCompute, 1)
})

test('a failed delete acknowledgement does not skip the rest of cleanup or final verification', async () => {
    const second = { id: 'e27ab421-8c19-4365-810b-7bd5df18ad21', name: `${prefix}-b` }
    const extendedScope = { ...scope, phases: [{ prefix, services: [owned, second] }] }
    const extendedJournal = { ...journal, services: [owned, second] }
    const before = { ...initial, services: [old, owned, second], instances: [old.id, owned.id, second.id] }
    const removed = []
    let snapshots = 0
    await assert.rejects(cleanupFixture(extendedScope, extendedJournal, initial, {
        inventory: async () => ++snapshots === 1 ? before : { ...initial, services: [old, owned], instances: [old.id, owned.id] },
        deleteService: async id => { removed.push(id); if (id === owned.id) throw new Error('missing acknowledgement') }
    }), /inventory/i)
    assert.deepEqual(removed, [owned.id, second.id])
    assert.equal(snapshots, 2)
})
