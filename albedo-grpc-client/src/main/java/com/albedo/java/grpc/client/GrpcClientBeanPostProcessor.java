package com.albedo.java.grpc.client;

import com.google.common.collect.Lists;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import lombok.SneakyThrows;

import javax.annotation.Resource;
import javax.inject.Inject;

/**
 * User: Michael
 * Email: yidongnan@gmail.com
 * Date: 5/17/16
 */
public class GrpcClientBeanPostProcessor implements org.springframework.beans.factory.config.BeanPostProcessor {

    private Map<String, List<Class>> beansToProcess = new HashMap<>();

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Autowired
    private GrpcChannelFactory channelFactory;
    @Resource
    private Environment env;

    public GrpcClientBeanPostProcessor() {
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class clazz = bean.getClass();
        do {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(GrpcClient.class)) {
                    if (!beansToProcess.containsKey(beanName)) {
                        beansToProcess.put(beanName, new ArrayList<Class>());
                    }
                    beansToProcess.get(beanName).add(clazz);
                }
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (beansToProcess.containsKey(beanName)) {
            Object target = getTargetBean(bean);
            for (Class clazz : beansToProcess.get(beanName)) {
                for (Field field : clazz.getDeclaredFields()) {
                    GrpcClient annotation = AnnotationUtils.getAnnotation(field, GrpcClient.class);
                    if (null != annotation) {

                        List<ClientInterceptor> list = Lists.newArrayList();
                        for (Class<? extends ClientInterceptor> clientInterceptorClass : annotation.interceptors()) {
                            ClientInterceptor clientInterceptor;
                            if (beanFactory.getBeanNamesForType(ClientInterceptor.class).length > 0) {
                                clientInterceptor = beanFactory.getBean(clientInterceptorClass);
                            } else {
                                try {
                                    clientInterceptor = clientInterceptorClass.newInstance();
                                } catch (InstantiationException | IllegalAccessException e) {
                                    throw new BeanCreationException("Failed to create interceptor instance", e);
                                }
                            }
                            list.add(clientInterceptor);
                        }
                        String name = annotation.context() ?
                                env.getProperty(annotation.value()) : annotation.value();
                        Channel channel = channelFactory.createChannel(name, list);
                        ReflectionUtils.makeAccessible(field);
                        ReflectionUtils.setField(field, target, channel);
                    }
                }
            }
        }
        return bean;
    }

    @SneakyThrows
    private Object getTargetBean(Object bean) {
        Object target = bean;
        while (AopUtils.isAopProxy(target)) {
            target = ((Advised) target).getTargetSource().getTarget();
        }
        return target;
    }


}
