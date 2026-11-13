import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { buildRehearsalSupervisor, sha256File } from '../lib/room-production-supervisor.mjs'

test('builds and extracts the supervisor from a pinned source snapshot', () => {
    const base = fs.mkdtempSync(path.join(os.tmpdir(), 'room-supervisor-'))
    try {
        const repo = path.join(base, 'repo')
        const output = path.join(base, 'output')
        const source = path.join(repo, 'tools/room-fault-supervisor')
        const recipe = path.join(repo, 'deploy/room-ownership/supervised/supervisor.Dockerfile')
        fs.mkdirSync(path.join(source, 'cmd/supervisor'), { recursive: true })
        fs.mkdirSync(path.dirname(recipe), { recursive: true })
        fs.mkdirSync(output)
        fs.writeFileSync(path.join(source, 'go.mod'), 'module test\ngo 1.27.0\n')
        fs.writeFileSync(path.join(source, 'cmd/supervisor/main.go'), 'package main\n')
        const compiler = 'golang@sha256:' + 'a'.repeat(64)
        fs.writeFileSync(recipe, 'FROM ' + compiler + ' AS build\nFROM scratch\n')
        const runId = 'eeeeeeee-0000-4000-8000-000000000001'
        const label = 'fasttowin.production-rehearsal=' + runId
        const name = 'room-pilot-eeeeeeee0000'
        const imageId = 'sha256:' + 'b'.repeat(64)
        const compilerId = 'sha256:' + 'c'.repeat(64)
        const commands = []
        const docker = args => {
            commands.push(args)
            if (args[0] === 'build') {
                const context = args.at(-1)
                assert.equal(fs.readFileSync(path.join(context, 'cmd/supervisor/main.go'), 'utf8'), 'package main\n')
                assert.ok(context.startsWith(output + path.sep))
                assert.equal(sha256File(args[args.indexOf('-f') + 1]), sha256File(recipe))
                assert.ok(args.includes(label))
            }
            if (args[0] === 'image') return args.at(-1) === compiler ? compilerId : imageId
            if (args[0] === 'cp') fs.writeFileSync(args[2], Buffer.from([0x7f, 0x45, 0x4c, 0x46, 1, 2, 3, 4]))
            return ''
        }
        const result = buildRehearsalSupervisor({ repo, output, name, label, docker })
        assert.equal(result.compiler, compiler)
        assert.equal(result.compilerImage, compilerId)
        assert.equal(result.image, imageId)
        assert.equal(result.binarySha256, sha256File(result.binary))
        assert.equal(result.dockerfileSha256, sha256File(recipe))
        assert.deepEqual(result.sources.map(file => file.path), ['cmd/supervisor/main.go', 'go.mod'])
        assert.deepEqual(commands.map(args => args[0]), ['image', 'build', 'image', 'create', 'cp', 'rm'])
        assert.ok(commands[3].includes(label))
        assert.ok(commands[3].includes(imageId))
        assert.deepEqual(commands[5], ['rm', '-v', name + '-supervisor-extract'])
        fs.writeFileSync(recipe, 'FROM golang:latest AS build\nFROM scratch\n')
        assert.throws(() => buildRehearsalSupervisor({ repo, output, name, label, docker }), /pinned/)
        assert.equal(commands.length, 6)
    } finally {
        fs.rmSync(base, { recursive: true, force: true })
    }
})
