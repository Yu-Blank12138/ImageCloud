package com.blank.imagecloud.config;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Locale;

/**
 * 统一将接口响应中以 id 结尾的 Long 字段序列化为字符串。
 * 雪花算法生成的 19 位 ID 超出 JavaScript 安全整数范围，必须以字符串返回才能避免前端精度丢失。
 */
@Configuration
public class JacksonLongIdConfig {

    /** 参与字符串序列化的字段名后缀。 */
    private static final String ID_SUFFIX = "id";

    /**
     * 注册 ID 字段字符串序列化模块，由 Spring Boot 自动装配到全局 ObjectMapper。
     *
     * @return 只修改响应序列化行为的 Jackson 模块
     */
    @Bean
    public Module longIdAsStringModule() {
        SimpleModule module = new SimpleModule("LongIdAsString");
        module.setSerializerModifier(new BeanSerializerModifier() {
            /**
             * 将 Long 类型且字段名以 id 结尾的属性替换为字符串序列化器。
             *
             * @param config 当前序列化配置
             * @param beanDesc 正在序列化的类型信息
             * @param beanProperties 该类型的可序列化属性
             * @return 处理后的属性列表
             */
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
                                                             List<BeanPropertyWriter> beanProperties) {
                for (BeanPropertyWriter writer : beanProperties) {
                    Class<?> rawType = writer.getType().getRawClass();
                    boolean isLong = rawType == Long.class || rawType == long.class;
                    if (isLong && writer.getName().toLowerCase(Locale.ROOT).endsWith(ID_SUFFIX)) {
                        writer.assignSerializer(ToStringSerializer.instance);
                    }
                }
                return beanProperties;
            }
        });
        return module;
    }
}
