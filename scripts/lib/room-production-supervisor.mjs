import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { ownedPath } from './room-supervised-local.mjs'

export function sha256File(file) {
    return createHash('sha256').update(fs.readFileSync(file)).digest('hex')
}

function snapshotSources(source, context, relative = '') {
    return fs.readdirSync(path.join(source, relative), { withFileTypes: true })
        .sort((first, second) => first.name < second.name ? -1 : first.name > second.name ? 1 : 0)
        .flatMap(entry => {
            const file = path.join(relative, entry.name)
            const input = path.join(source, file)
            const target = ownedPath(context, path.join(context, file))
            if (entry.isDirectory()) {
                fs.mkdirSync(target)
                return snapshotSources(source, context, file)
            }
            assert(entry.isFile(), 'Supervisor source must contain only regular files and directories')
            fs.copyFileSync(input, target, fs.constants.COPYFILE_EXCL)
            return [{ path: file.replaceAll('\\', '/'), bytes: fs.statSync(target).size, sha256: sha256File(target) }]
        })
}

export function buildRehearsalSupervisor({ repo, output, name, label, docker }) {
    assert.match(name, /^room-pilot-[a-f0-9]{12}$/)
    assert.match(label, /^fasttowin\.production-rehearsal=[a-f0-9-]{36}$/)
    const source = ownedPath(repo, path.join(repo, 'tools/room-fault-supervisor'))
    const recipe = ownedPath(repo, path.join(repo, 'deploy/room-ownership/supervised/supervisor.Dockerfile'))
    const recipeBytes = fs.readFileSync(recipe)
    const stages = [...recipeBytes.toString('utf8').matchAll(/^FROM\s+(\S+)/gm)].map(match => match[1])
    assert(stages.length === 2 && /^golang@sha256:[a-f0-9]{64}$/.test(stages[0]) && stages[1] === 'scratch',
        'Supervisor Dockerfile must use a pinned Go compiler and a scratch runtime')
    const [compiler] = stages
    const compilerImage = docker(['image', 'inspect', '--format', '{{.Id}}', compiler])
    assert.match(compilerImage, /^sha256:[a-f0-9]{64}$/)

    const context = ownedPath(output, path.join(output, 'supervisor-build'))
    fs.mkdirSync(context)
    const sources = snapshotSources(source, context)
    const dockerfile = path.join(context, 'Dockerfile')
    fs.writeFileSync(dockerfile, recipeBytes, { flag: 'wx' })
    const tag = name + '-supervisor'
    docker(['build', '--pull=false', '--network', 'none', '--label', label, '-t', tag, '-f', dockerfile, context], {}, 300_000)
    const image = docker(['image', 'inspect', '--format', '{{.Id}}', tag])
    assert.match(image, /^sha256:[a-f0-9]{64}$/)

    const extract = name + '-supervisor-extract'
    const binary = ownedPath(output, path.join(output, 'supervisor'))
    assert(!fs.existsSync(binary), 'Supervisor binary destination already exists')
    docker(['create', '--network', 'none', '--label', label, '--name', extract, image, '/supervisor'])
    try {
        docker(['cp', extract + ':/supervisor', binary])
        const bytes = fs.readFileSync(binary)
        assert(bytes.subarray(0, 4).equals(Buffer.from([0x7f, 0x45, 0x4c, 0x46])), 'Expected Linux supervisor executable')
    } finally {
        docker(['rm', '-v', extract])
    }
    return { binary, binarySha256: sha256File(binary), dockerfileSha256: sha256File(dockerfile), compiler, compilerImage, image, sources }
}
