import assert from 'node:assert/strict'

const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/
const order = (left, right) => left.id.localeCompare(right.id)

function serviceMap(rows) {
    const names = new Map()
    const ids = new Set()
    for (const { id, name } of rows) {
        assert(uuid.test(id) && typeof name === 'string' && name.length > 0, 'Invalid service identity')
        assert(!names.has(name) && !ids.has(id), 'Duplicate service identity')
        names.set(name, id)
        ids.add(id)
    }
    return names
}

function sameTarget(scope, inventory) {
    assert(inventory.project === scope.project && inventory.environment === scope.environment &&
        inventory.workspace === scope.workspace, 'Staging project, environment or workspace mismatch')
}

function sameVolumes(inventory, baseline) {
    assert.deepEqual([...inventory.volumes].sort(order), [...baseline.volumes].sort(order), 'Persistent volume metadata changed')
    assert.deepEqual([...inventory.attachments].sort(order), [...baseline.attachments].sort(order), 'Persistent volume attachments changed')
}

/** Called only with a creation journal bound to the approved scope. Never delete by name. */
export async function cleanupFixture(scope, journal, baseline, api) {
    sameTarget(scope, baseline)
    assert(journal.project === scope.project && journal.environment === scope.environment, 'Journal environment mismatch')
    const phase = scope.phases.find(phase => phase.prefix === journal.prefix)
    assert(phase && /^room-pilot-[0-9a-f]{12}$/.test(phase.prefix), 'Unknown fixture identity')
    const expected = new Set(phase.services.map(service => service.name))
    const prior = serviceMap(baseline.services)
    const owned = serviceMap(journal.services)
    const priorIds = new Set(prior.values())
    for (const [name, id] of owned) {
        assert(expected.has(name) && name.startsWith(`${phase.prefix}-`) && !prior.has(name) &&
            !priorIds.has(id), 'Unproven fixture service identity')
    }

    const live = await api.inventory()
    sameTarget(scope, live)
    const current = serviceMap(live.services)
    const instances = new Set(live.instances)
    sameVolumes(live, baseline)
    for (const [name, id] of prior) assert.equal(current.get(name), id, 'Existing service identity changed')
    for (const [name, id] of current) {
        if (!prior.has(name)) assert(owned.get(name) === id && instances.has(id), 'Unproven service in staging inventory')
    }
    const present = [...owned].filter(([name, id]) => current.get(name) === id).map(([, id]) => id)
    assert.deepEqual([...live.instances].sort(), [...baseline.instances, ...present].sort(), 'Unexpected environment inventory')
    let unacknowledgedDeletes = 0
    for (const id of present) {
        try {
            await api.deleteService(id)
        } catch {
            unacknowledgedDeletes++
        }
    }

    const after = await api.inventory()
    sameTarget(scope, after)
    assert.deepEqual(serviceMap(after.services), prior, 'Service inventory differs after cleanup')
    assert.deepEqual([...after.instances].sort(), [...baseline.instances].sort(), 'Environment inventory differs after cleanup')
    sameVolumes(after, baseline)
    assert.equal(after.activeCompute, 0, 'Prior staging compute was not verified stopped')
    return { ownedServicesRemaining: 0, existingServicesVerified: prior.size, volumesVerified: baseline.volumes.length,
        unacknowledgedDeletes }
}

export function railwayCleanupApi(query, scope) {
    return {
        async inventory() {
            const { project } = await query(`query($p:String!){project(id:$p){id name workspaceId
                services{edges{node{id name}}} volumes{edges{node{id name createdAt}}}
                environments{edges{node{id name volumeInstances{edges{node{id serviceId environmentId volumeId mountPath}}}
                    serviceInstances{edges{node{serviceId activeDeployments{id status deploymentStopped}
                        latestDeployment{id status deploymentStopped}}}}}}}}}`, { p: scope.project })
            const nodes = connection => connection.edges.map(edge => edge.node)
            const environments = nodes(project.environments)
            assert(project.name === 'FastToWin Staging' && environments.length === 1 && environments[0].name === 'staging',
                'Expected only the FastToWin Staging environment')
            const [env] = environments
            const instances = nodes(env.serviceInstances)
            return {
                project: project.id, environment: env.id, workspace: project.workspaceId,
                services: nodes(project.services), instances: instances.map(instance => instance.serviceId),
                volumes: nodes(project.volumes), attachments: nodes(env.volumeInstances),
                activeCompute: new Set(instances.flatMap(instance => [...instance.activeDeployments, instance.latestDeployment])
                    .filter(deployment => deployment && (deployment.status === 'REMOVING' ||
                        !deployment.deploymentStopped && !['FAILED', 'CRASHED', 'REMOVED', 'SKIPPED'].includes(deployment.status)))
                    .map(deployment => deployment.id)).size
            }
        },
        async deleteService(id) {
            assert(uuid.test(id), 'Invalid service ID')
            const result = await query('mutation($s:String!){serviceDelete(id:$s)}', { s: id })
            assert.equal(result.serviceDelete, true, 'Railway did not acknowledge service removal')
        }
    }
}
