local socket = ARGV[1]
local state = tonumber(ARGV[2])
for index = 1, 3 do
    if index ~= state then redis.call('ZREM', KEYS[index], socket) end
end
if state > 0 then
    local time = redis.call('TIME')
    local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
    local lease = tonumber(ARGV[3])
    local key = KEYS[state]
    redis.call('ZREMRANGEBYSCORE', key, '-inf', now)
    redis.call('ZADD', key, now + lease, socket)
    redis.call('PEXPIRE', key, lease)
end
return 1
