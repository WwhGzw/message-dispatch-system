package com.msg.access.filter;

import com.msg.common.util.HmacSignatureUtil;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dubbo RPC 鉴权过滤器
 * <p>
 * 调用方在 RpcContext 中设置 appKey、signature、timestamp，
 * 服务端通过此 Filter 验证 HMAC-SHA256 签名。
 * <p>
 * 调用方示例：
 * <pre>
 * RpcContext.getContext().setAttachment("appKey", "myAppKey");
 * RpcContext.getContext().setAttachment("signature", HmacSignatureUtil.generateSignature(secret, body + timestamp));
 * RpcContext.getContext().setAttachment("timestamp", String.valueOf(System.currentTimeMillis()));
 * </pre>
 */
@Activate(group = CommonConstants.PROVIDER, order = -1000)
public class AuthDubboFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AuthDubboFilter.class);
    private static final long TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000L;

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String appKey = invocation.getAttachment("appKey");
        String signature = invocation.getAttachment("signature");
        String timestamp = invocation.getAttachment("timestamp");

        // 如果没有传鉴权信息，跳过（内部调用场景）
        if (appKey == null && signature == null) {
            return invoker.invoke(invocation);
        }

        if (appKey == null || signature == null || timestamp == null) {
            log.warn("Dubbo 鉴权失败: 缺少必要参数. appKey={}, signature={}, timestamp={}",
                    appKey != null, signature != null, timestamp != null);
            throw new RpcException(RpcException.AUTHORIZATION_EXCEPTION, "鉴权失败: 缺少必要参数");
        }

        // 时间戳校验
        try {
            long ts = Long.parseLong(timestamp);
            if (Math.abs(System.currentTimeMillis() - ts) > TIMESTAMP_TOLERANCE_MS) {
                throw new RpcException(RpcException.AUTHORIZATION_EXCEPTION, "鉴权失败: 时间戳过期");
            }
        } catch (NumberFormatException e) {
            throw new RpcException(RpcException.AUTHORIZATION_EXCEPTION, "鉴权失败: 时间戳格式错误");
        }

        // TODO: 从配置中心或数据库查询 appKey 对应的 secret
        // 当前为占位逻辑，生产环境需替换
        log.debug("Dubbo 鉴权通过: appKey={}", appKey);
        return invoker.invoke(invocation);
    }
}
