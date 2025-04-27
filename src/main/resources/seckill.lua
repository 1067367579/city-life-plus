-- 从参数中获取出优惠券ID和下单操作的用户ID
local voucherId = ARGV[1]
local userId = ARGV[2]
-- 拼接出redis的key
local stockKey = 'seckill:stock:'..voucherId
local orderKey = 'seckill:order:'..voucherId
-- 获取库存 从redis中 给出一个默认值 避免出错
local stock = redis.call('get',stockKey) or '0'
if(tonumber(stock) < 1) then
    return 1;
end
-- 在set集合中找看是否已经下单
if(redis.call('sismember',orderKey,userId) == 1) then
    return 2;
end
-- 更新缓存当中的库存和下单用户集合
redis.call('incrby','seckill:stock:'..voucherId,-1);
redis.call('sadd','seckill:order:'..voucherId,userId);
return 0;