package edu.zzttc.backend.utils;

import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.Map;

/**
 * 简易模板渲染器：从 classpath 读取 HTML 模板，使用 {{key}} 变量占位符做字符串替换。
 * 为避免引入额外依赖，此工具仅做最基础的占位替换，不支持循环/条件。
 */
public class TemplateRenderer {
    public static String render(String classpathLocation, Map<String, Object> model) {
        try {
            ClassPathResource res = new ClassPathResource(classpathLocation);
            try (InputStream is = res.getInputStream()) {
                String tpl = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (model != null) {
                    for (Map.Entry<String, Object> e : model.entrySet()) {
                        String key = e.getKey();
                        String val = e.getValue() == null ? "" : String.valueOf(e.getValue());
                        tpl = tpl.replace("{{" + key + "}}", val);
                    }
                }
                return tpl;
            }
        } catch (Exception e) {
            // 失败则返回空字符串，调用方可回退至纯文本
            return "";
        }
    }
}

