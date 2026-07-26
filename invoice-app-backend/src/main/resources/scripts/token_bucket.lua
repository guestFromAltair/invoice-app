local key      = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate     = tonumber(ARGV[2])

local time = redis.call('TIME')
local now  = tonumber(time[1]) + (tonumber(time[2]) / 1000000)

local bucket = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(bucket[1])
local ts     = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    ts     = now
end

local elapsed = math.max(0, now - ts)
tokens = math.min(capacity, tokens + (elapsed * rate))

local allowed = 0
if tokens >= 1 then
    tokens  = tokens - 1
    allowed = 1
end

redis.call('HSET', key, 'tokens', tokens, 'ts', now)

redis.call('EXPIRE', key, math.ceil(capacity / rate) + 1)

local retry_after = 0
if allowed == 0 then
    retry_after = math.ceil((1 - tokens) / rate)
end

return { allowed, math.floor(tokens), retry_after }