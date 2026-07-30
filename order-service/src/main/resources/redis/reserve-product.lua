local available = tonumber(redis.call('HGET', KEYS[1], ARGV[1]))
local requested = tonumber(ARGV[2])

if available == nil then
    return -1
end

if requested == nil or requested < 1 or available < requested then
    return 0
end

redis.call('HINCRBY', KEYS[1], ARGV[1], -requested)
return 1
