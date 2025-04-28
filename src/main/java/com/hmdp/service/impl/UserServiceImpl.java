package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.CacheConstants;
import com.hmdp.domain.dto.LoginFormDTO;
import com.hmdp.domain.dto.Result;
import com.hmdp.domain.entity.User;
import com.hmdp.domain.vo.UserVO;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.EmailService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserMapper userMapper;

    @Override
    public Result sendCode(String phone) {
        //先校验邮箱号
        if(RegexUtils.isEmailInvalid(phone)) {
            //校验不通过 直接返回错误
            return Result.fail("邮箱号不正确！");
        }
        //校验验证码的剩余时间，要点击一次一分钟之后才能点击下一次，一天只能最多登录50次
        Long ttl = stringRedisTemplate.getExpire(CacheConstants.LOGIN_CODE_PREFIX + phone,TimeUnit.SECONDS);
        if(120L - ttl <= 60L) {
            return Result.fail("您已请求验证码，请稍后再试");
        }
        String countStr = stringRedisTemplate.opsForValue().get(CacheConstants.LOGIN_CODE_TIME_PREFIX + phone);
        if(countStr!=null && Long.parseLong(countStr) > 50) {
            return Result.fail("您请求验证码频率过高，请稍后再试");
        }
        //邮箱号通过 生成验证码
        String code = RandomUtil.randomNumbers(6);
        //放到redis当中 两分钟有效
        stringRedisTemplate.opsForValue().set(CacheConstants.LOGIN_CODE_PREFIX+phone,code,
                120L,TimeUnit.SECONDS);
        //邮箱服务发送消息
        emailService.sendSimpleMail(phone,code);
        //次数加1
        stringRedisTemplate.opsForValue().increment(CacheConstants.LOGIN_CODE_TIME_PREFIX+phone,1);
        //如果此时counter是第一次设置 就设置过期时间
        if(countStr == null) {
            stringRedisTemplate.expire(CacheConstants.LOGIN_CODE_TIME_PREFIX+phone,
                    ChronoUnit.SECONDS.between(LocalDateTime.now(),LocalDateTime.now().plusDays(1)
                            .plusHours(0).plusMinutes(0).plusSeconds(0).plusNanos(0)
                    ), TimeUnit.SECONDS);
        }
        //返回成功信息
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        //验证邮箱号
        String phone = loginForm.getPhone();
        if(RegexUtils.isEmailInvalid(phone)) {
            //校验不通过 直接返回错误
            return Result.fail("邮箱号不正确！");
        }
        //获取redis中的code，比对
        String code = stringRedisTemplate.opsForValue().get(CacheConstants.LOGIN_CODE_PREFIX + phone);
        if(code == null) {
            return Result.fail("验证码已过期！");
        }
        if(!code.equals(loginForm.getCode())) {
            return Result.fail("验证码错误！");
        }
        //删除验证码 已经通过校验
        stringRedisTemplate.delete(CacheConstants.LOGIN_CODE_PREFIX+phone);
        //拿到邮箱号 去数据库检索是否有该用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone)
        );
        if(user == null) {
            user = createNewUser(phone);
        }
        //此时user有数据 有ID 插入redis缓存 生成token 随机数
        String token = UUID.randomUUID().toString();
        //生成一个UserVO的Map 插入Hash类型的缓存当中
        UserVO userVO = BeanUtil.toBean(user, UserVO.class);
        Map<String, Object> userVOMap = BeanUtil.beanToMap(userVO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue!=null?fieldValue.toString():"")
        );
        //存入redis 设置过期时间
        stringRedisTemplate.opsForHash().putAll(CacheConstants.LOGIN_TOKEN_PREFIX+token,userVOMap);
        stringRedisTemplate.expire(CacheConstants.LOGIN_TOKEN_PREFIX+token,30L, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        stringRedisTemplate.delete(CacheConstants.LOGIN_TOKEN_PREFIX+token);
        return Result.ok();
    }

    @Override
    public Result getUserVO(Long userId) {
        String json = stringRedisTemplate.opsForValue()
                .get(CacheConstants.USER_VO_KEY + userId);
        if(StrUtil.isNotBlank(json)) {
            return Result.ok(JSON.parseObject(json,UserVO.class));
        }
        User user = userMapper.selectById(userId);
        if(user == null) {
            return Result.fail("用户不存在！");
        }
        UserVO userVO = BeanUtil.toBean(user,UserVO.class);
        stringRedisTemplate.opsForValue().set(CacheConstants.USER_VO_KEY+userId, JSON.toJSONString(userVO)
        , 30L, TimeUnit.MINUTES);
        return Result.ok(userVO);
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        LocalDateTime now = LocalDateTime.now();
        String formatTime = formatter.format(now);
        String key = CacheConstants.USER_SIGN_KEY_PREFIX+userId+":"+formatTime;
        //获取当天在一个月中的日期 作为offset
        int dayOfMonth = now.getDayOfMonth();
        //到redis中进行设置
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth, true);
        return Result.ok();
    }

    @Override
    public Result getSignDays() {
        //获取连续签到数据 逐位比对
        Long userId = UserHolder.getUser().getId();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
        LocalDateTime now = LocalDateTime.now();
        String formatTime = formatter.format(now);
        String key = CacheConstants.USER_SIGN_KEY_PREFIX+userId+":"+formatTime;
        int dayOfMonth = now.getDayOfMonth();
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth+1)).valueAt(0));
        if(CollectionUtil.isEmpty(result)) {
            return Result.ok(0);
        }
        //获取出元素 逐位比对
        Long num = result.get(0);
        if(num == null || num == 0) {
            return Result.ok(0);
        }
        int count = 0;
        //右移 获取出最后一位进行比对
        while(true) {
            if(((num & 1) == 0)) {
                break;
            } else {
                count++;
            }
            //无符号右移 注意
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createNewUser(String phone) {
        User user = new User();
        //执行插入用户逻辑
        user.setPhone(phone);
        user.setNickName("用户"+RandomUtil.randomString(6));
        user.setIcon("https://mdphotos2.oss-cn-shenzhen.aliyuncs.com/img/default_handsome.jpg");
        //插入
        userMapper.insert(user);
        return user;
    }


}
