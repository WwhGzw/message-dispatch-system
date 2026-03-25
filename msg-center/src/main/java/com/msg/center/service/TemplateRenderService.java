package com.msg.center.service;

import com.msg.center.exception.TemplateRenderException;
import com.msg.common.entity.MessageTemplateEntity;
import com.msg.common.mapper.MessageTemplateMapper;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 模板渲染服务
 * <p>
 * 集成 Freemarker 引擎，支持 Redis 缓存优先读取模板内容。
 * Redis 不可用时降级到 DB 直接查询。
 */
@Service
public class TemplateRenderService {

    private static final Logger log = LoggerFactory.getLogger(TemplateRenderService.class);

    private static final String CACHE_KEY_PREFIX = "tpl:";
    private static final long CACHE_TTL_HOURS = 24;
    private static final Pattern UNREPLACED_PLACEHOLDER = Pattern.compile("\\$\\{");

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MessageTemplateMapper templateMapper;

    private Configuration freemarkerConfig;

    @PostConstruct
    public void init() {
        freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        freemarkerConfig.setDefaultEncoding("UTF-8");
        freemarkerConfig.setLogTemplateExceptions(false);
        freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    /**
     * 渲染模板
     * <p>
     * 流程：
     * 1. 从 Redis 缓存读取模板内容
     * 2. 缓存未命中 → 从 DB 加载并写入缓存
     * 3. 使用 Freemarker 渲染
     * 4. 校验渲染结果不包含未替换的占位符
     *
     * @param templateCode 模板编码
     * @param variables    变量 Map
     * @return 渲染后的内容
     * @throws TemplateRenderException 模板不存在/未启用/变量缺失/占位符未替换
     */
    public String renderTemplate(String templateCode, Map<String, Object> variables) {
        String content = loadTemplateContent(templateCode);
        String rendered = doRender(templateCode, content, variables);
        validateNoUnreplacedPlaceholders(templateCode, rendered);
        return rendered;
    }

    /**
     * 加载模板内容：Redis 缓存优先，缓存未命中从 DB 加载
     */
    String loadTemplateContent(String templateCode) {
        String cacheKey = CACHE_KEY_PREFIX + templateCode;

        // Step 1: 尝试从 Redis 缓存读取
        String cached = null;
        boolean redisAvailable = true;
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Redis 不可用，降级到 DB 查询模板. templateCode={}", templateCode, e);
            redisAvailable = false;
        }

        if (cached != null) {
            return cached;
        }

        // Step 2: 缓存未命中 → 从 DB 加载
        MessageTemplateEntity entity = templateMapper.selectByTemplateCode(templateCode);
        if (entity == null) {
            throw new TemplateRenderException("模板不存在: " + templateCode);
        }
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new TemplateRenderException("模板未启用: " + templateCode);
        }

        String content = entity.getContent();

        // Step 3: 写入 Redis 缓存（Redis 不可用时跳过）
        if (redisAvailable) {
            try {
                redisTemplate.opsForValue().set(cacheKey, content, CACHE_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("写入 Redis 缓存失败. templateCode={}", templateCode, e);
            }
        }

        return content;
    }

    /**
     * 使用 Freemarker 渲染模板内容
     */
    private String doRender(String templateCode, String content, Map<String, Object> variables) {
        try {
            StringTemplateLoader loader = new StringTemplateLoader();
            loader.putTemplate(templateCode, content);

            Configuration cfg = (Configuration) freemarkerConfig.clone();
            cfg.setTemplateLoader(loader);

            Template template = cfg.getTemplate(templateCode);
            StringWriter writer = new StringWriter();
            template.process(variables, writer);
            return writer.toString();
        } catch (TemplateException e) {
            String msg = String.format("模板渲染失败，变量缺失或语法错误. templateCode=%s, error=%s",
                    templateCode, e.getMessage());
            log.error(msg, e);
            throw new TemplateRenderException(msg, e);
        } catch (Exception e) {
            String msg = String.format("模板渲染异常. templateCode=%s, error=%s",
                    templateCode, e.getMessage());
            log.error(msg, e);
            throw new TemplateRenderException(msg, e);
        }
    }

    /**
     * 校验渲染结果中不包含未替换的占位符 ${...}
     */
    private void validateNoUnreplacedPlaceholders(String templateCode, String rendered) {
        if (UNREPLACED_PLACEHOLDER.matcher(rendered).find()) {
            String msg = String.format("渲染结果包含未替换的占位符. templateCode=%s", templateCode);
            log.error(msg);
            throw new TemplateRenderException(msg);
        }
    }
}
