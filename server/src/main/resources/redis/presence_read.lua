local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local live = '(' .. tostring(now)
local results = {}
for index = 1, #KEYS, 3 do
    local state = 0
    for candidate = 3, 1, -1 do
        if redis.call('ZCOUNT', KEYS[index + candidate - 1], live, '+inf') > 0 then
            state = candidate
            break
        end
    end
    results[#results + 1] = state
end
return results
