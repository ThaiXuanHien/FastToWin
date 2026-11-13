import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { candidateIdentity, pairedRouteAccounts } from '../lib/room-production-manifest.mjs'

const sha = text => createHash('sha256').update(text).digest('hex')
const a = '00000000-0000-4000-8000-00000000000a'
const b = '00000000-0000-4000-8000-00000000000b'

test('candidate identity binds exact ordered Gradle launcher JAR bytes', () => {
    const jars = { 'server.jar': 'a'.repeat(64), 'dep.jar': 'b'.repeat(64) }
    const entry = name => '$APP_HOME/lib/' + name
    const candidate = candidateIdentity('CLASSPATH=' + [entry('server.jar'), entry('dep.jar')].join(':'), name => jars[name])
    const classpathSha256 = sha('fasttowin/runtime-classpath/v1\nserver.jar ' + jars['server.jar'] + '\ndep.jar ' + jars['dep.jar'] + '\n')
    assert.equal(candidate.serverJarSha256, jars['server.jar'])
    assert.equal(candidate.classpathSha256, classpathSha256)
    assert.equal(candidate.candidateSha256, sha('fasttowin/candidate/v1\n' + jars['server.jar'] + '\n' + classpathSha256 + '\n'))
    const reordered = candidateIdentity('CLASSPATH=' + [entry('dep.jar'), entry('server.jar')].join(':'), name => jars[name])
    assert.notEqual(reordered.candidateSha256, candidate.candidateSha256)
    for (const line of ['CLASSPATH=$APP_HOME/lib/*', 'CLASSPATH=$APP_HOME/lib/../server.jar', 'CLASSPATH=' + entry('dep.jar')]) {
        assert.throws(() => candidateIdentity(line, name => jars[name]))
    }
})

test('each synthetic pair includes one deterministic route to A and one to B', () => {
    let sequence = 0
    const route = account => {
        const digest = createHash('sha256').update('fasttowin/owned-create-routing/v1\naccount=' + account + '\nnodes=' + [a, b].join(',') + '\n').digest()
        return [a, b][digest.at(-1) % 2]
    }
    const ids = pairedRouteAccounts(a, b, 20, () => '00000000-0000-4000-8000-' + (++sequence).toString(16).padStart(12, '0'))
    assert.equal(new Set(ids.length === 40 ? ids : []).size, 40)
    for (let i = 0; i < ids.length; i += 2) {
        assert.deepEqual([route(ids[i]), route(ids[i + 1])], [a, b])
    }
})
