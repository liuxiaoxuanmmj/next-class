package edu.zzttc.backend.filter;

import edu.zzttc.backend.domain.entity.RestBean;
import edu.zzttc.backend.utils.Const;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Order(Const.ORDER_LIMIT)
public class FlowLimitFilter extends HttpFilter {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 限流配置
    private static final int MAX_REQUESTS = 10;    // 最大请求次数
    private static final int WINDOW_SECONDS = 3;   // 限流时间窗口(秒)
    private static final int BLOCK_SECONDS = 30;   // 封禁时长(秒)

    @Override
    protected void doFilter(HttpServletRequest request,
                            HttpServletResponse response,
                            FilterChain chain)
            throws IOException, ServletException {

        String path = request.getRequestURI();

        // 1. 跨域预检 & 文档相关路径不过限流
        if (isPreflight(request) || isDocPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 2. 真实业务请求限流
        String ip = request.getRemoteAddr();

        // 已封禁
        if (isBlocked(ip)) {
            writeBlockMessage(response);
            return;
        }

        // 超过阈值 -> 加入封禁
        if (isLimitExceeded(ip)) {
            blockIp(ip);
            writeBlockMessage(response);
            return;
        }

        // 通过校验
        chain.doFilter(request, response);
    }

    /**
     * 是否为跨域预检请求
     */
    private boolean isPreflight(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    /**
     * 文档 / 静态资源等不参与限流的路径
     */
    private boolean isDocPath(String path) {
        if (path == null) return false;
        return path.equals("/doc.html")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/webjars")
                || path.equals("/favicon.ico")
                || path.equals("/error");
    }

    /**
     * 判断 IP 是否被封禁
     */
    private boolean isBlocked(String ip) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(Const.FLOW_LIMIT_BLOCK + ip)
        );
    }

    /**
     * 判断限流是否超过阈值
     */
    private boolean isLimitExceeded(String ip) {
        String counterKey = Const.FLOW_LIMIT_COUNTER + ip;
        long count = stringRedisTemplate.opsForValue().increment(counterKey);
        if (count == 1) {
            // 第一次请求，设置过期时间
            stringRedisTemplate.expire(counterKey, WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        return count > MAX_REQUESTS;
    }

    /**
     * 封禁 IP
     */
    private void blockIp(String ip) {
        stringRedisTemplate
                .opsForValue()
                .set(Const.FLOW_LIMIT_BLOCK + ip, "1", BLOCK_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 返回 403 JSON 提示
     */
    private void writeBlockMessage(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter()
                .write(RestBean.forbidden("操作频繁，请稍后重试").asJsonString());
    }
}
