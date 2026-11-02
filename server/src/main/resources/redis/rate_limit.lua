local capacity = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
if not capacity or capacity < 1 or capacity > 1000000 or capacity ~= math.floor(capacity) or not window or window < 1 or window > 86400000 or window ~= math.floor(window) then return {3, 0} end
local kind = redis.call('TYPE', KEYS[1]).ok
if kind ~= 'none' and kind ~= 'hash' then return {3, 0} end
local tokens = capacity
local last = 0
if kind == 'hash' then
    if redis.call('HLEN', KEYS[1]) ~= 4 then return {3, 0} end
    local values = redis.call('HMGET', KEYS[1], 'tokens', 'last', 'capacity', 'window')
    tokens, last = tonumber(values[1]), tonumber(values[2])
    local oldCapacity, oldWindow = tonumber(values[3]), tonumber(values[4])
    if not tokens or not last or not oldCapacity or not oldWindow or tokens ~= tokens or last ~= last or oldCapacity ~= oldCapacity or oldWindow ~= oldWindow or tokens < 0 or tokens > oldCapacity or last < 0 or last > 9007199254740991 or last ~= math.floor(last) or oldCapacity < 1 or oldCapacity > 1000000 or oldCapacity ~= math.floor(oldCapacity) or oldWindow < 1 or oldWindow > 86400000 or oldWindow ~= math.floor(oldWindow) then return {3, 0} end
    if oldCapacity ~= capacity or oldWindow ~= window then return {2, 0} end
end
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
if kind == 'none' then last = now end
local nextTime = math.max(now, last)
tokens = math.min(capacity, tokens + (nextTime - last) * capacity / window)
local allowed, retry = 0, 0
if tokens >= 1 then tokens = tokens - 1; allowed = 1 else retry = math.max(1, math.ceil((1 - tokens) * window / capacity)) end
redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'last', tostring(nextTime), 'capacity', tostring(capacity), 'window', tostring(window))
redis.call('PEXPIRE', KEYS[1], math.max(900000, window))
return {allowed, retry}
