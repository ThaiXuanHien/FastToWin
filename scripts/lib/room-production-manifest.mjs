import assert from 'node:assert/strict'
import { createHash, randomUUID } from 'node:crypto'

const sha = value => createHash('sha256').update(value).digest('hex')
const hex = /^[a-f0-9]{64}$/
const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/

export function candidateIdentity(launcher, hashJar) {
    const assignments = launcher.split(/\r?\n/).filter(line => line.startsWith('CLASSPATH='))
    assert.equal(assignments.length, 1, 'Expected one ordered Gradle classpath assignment')
    const jarFiles = assignments[0].slice('CLASSPATH='.length).split(':').map(entry => {
        const match = /^\$APP_HOME\/lib\/([A-Za-z0-9_.+-]+\.jar)$/.exec(entry)
        assert(match, 'Unexpected Gradle classpath entry')
        const sha256 = hashJar(match[1])
        assert.match(sha256, hex)
        return { name: match[1], sha256 }
    })
    assert.equal(new Set(jarFiles.map(file => file.name)).size, jarFiles.length, 'Duplicate classpath entry')
    const server = jarFiles.find(file => file.name === 'server.jar')
    assert(server, 'Server JAR is absent from classpath')
    const classpath = 'fasttowin/runtime-classpath/v1\n' + jarFiles.map(file => file.name + ' ' + file.sha256 + '\n').join('')
    const classpathSha256 = sha(classpath)
    return {
        serverJarSha256: server.sha256,
        classpathSha256,
        candidateSha256: sha('fasttowin/candidate/v1\n' + server.sha256 + '\n' + classpathSha256 + '\n'),
        jarFiles
    }
}

export function pairedRouteAccounts(ingress, worker, pairCount, nextId = randomUUID) {
    assert(uuid.test(ingress) && uuid.test(worker) && ingress !== worker)
    assert(Number.isSafeInteger(pairCount) && pairCount > 0 && pairCount <= 128)
    const nodes = [ingress, worker].sort()
    const byNode = new Map(nodes.map(node => [node, []]))
    const seen = new Set()
    for (let i = 0; i < pairCount * 64 && [...byNode.values()].some(ids => ids.length < pairCount); i++) {
        const id = nextId()
        assert(uuid.test(id) && !seen.has(id), 'Duplicate or invalid synthetic account ID')
        seen.add(id)
        const text = 'fasttowin/owned-create-routing/v1\naccount=' + id + '\nnodes=' + nodes.join(',') + '\n'
        const node = nodes[Number.parseInt(sha(text).slice(-2), 16) % nodes.length]
        const ids = byNode.get(node)
        if (ids.length < pairCount) ids.push(id)
    }
    const a = byNode.get(ingress)
    const b = byNode.get(worker)
    assert(a.length === pairCount && b.length === pairCount, 'Could not generate balanced route vectors')
    return a.flatMap((id, index) => [id, b[index]])
}
