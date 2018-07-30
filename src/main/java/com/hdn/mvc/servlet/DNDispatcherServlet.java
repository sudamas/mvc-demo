package com.hdn.mvc.servlet;

import com.hdn.mvc.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DNDispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();

    private List<String> classes = new ArrayList<String>();

    private Map<String, Object> ioc = new HashMap<String, Object>();

    private List<Handler> handlerMapping = new ArrayList<Handler>();


    @Override
    public void init(ServletConfig config) throws ServletException {
        //System.out.println("初始化完成");
//        1.加载配置文件
        initConfig(config.getInitParameter("contextConfiguration"));

//        2.根据配置文件扫描所有相关的类
        initScanner(properties.getProperty("scanPackage"));
//        3.初始化所有相关类的实例，并且将其放入到IOC容器中（map）
        initIOC();
//        4.实现自动化依赖注入
        initAutowried();
//        5.初始化handlerMapping
        initHandlerMapping();

    }

    private class Handler {

        Object controller;
        Method method;
        Pattern pattern;
        Map<String, Integer> paramIndex;

        public Handler(Object controller, Method method, Pattern pattern) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramIndex = new HashMap<String, Integer>();

            putParamIndex(method);
        }

        public void putParamIndex(Method method) {
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof DNRequestParam) {
                        String paramName = ((DNRequestParam) annotation).value().trim();
                        if (!"".equals(paramName)) {
                            paramIndex.put(paramName, i);
                        }
                    }
                }
            }

            Class<?>[] types = method.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (types[i] == HttpServletRequest.class || types[i] == HttpServletResponse.class) {
                    paramIndex.put(types[i].getName(), i);
                }
            }
        }

    }

    private void initHandlerMapping() {

        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(DNController.class)) {
                continue;
            }
            String baseUrl = clazz.getAnnotation(DNController.class).value().trim();
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(DNRequestMapping.class)) {
                    continue;
                }
                String url = ("/" + baseUrl + "/" +
                        method.getAnnotation(DNRequestMapping.class).value().trim())
                        .replaceAll("/+", "/");

                handlerMapping.add(new Handler(entry.getValue(), method, Pattern.compile(url)));

            }

        }

    }

    public void doHandler(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, InvocationTargetException, IllegalAccessException {
        Handler handler = patternHandler(request);
        if (handler == null) {
            response.setContentType("text/html;charset=utf-8");
            response.getWriter().write("您访问的页面不存在");
            return;
        }

        Class<?>[] types = handler.method.getParameterTypes();
        Object[] values = new Object[types.length];
        Map<String, String[]> parameterMap = request.getParameterMap();

        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {

            String value = Arrays.toString(entry.getValue()).replaceAll("\\[|\\]", "")
                    .replaceAll(",\\s", ",");// '//s'指空格

            if (!handler.paramIndex.containsKey(entry.getKey())) {
                continue;
            }
            Integer index = handler.paramIndex.get(entry.getKey());
            values[index] = convert(types[index], value);
        }
        handler.method.invoke(handler, handler.controller, values);
    }

    public Object convert(Class<?> type, String value) {
        if (type == Integer.class) {
            return Integer.valueOf(value);
        }
        return type;
    }

    private Handler patternHandler(HttpServletRequest request) {
        if (handlerMapping.isEmpty()) {
            return null;
        }
        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        url.replace(contextPath, "");
        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);
            if (!matcher.matches()) {
                continue;
            }
            return handler;
        }
        return null;
    }

    private void initAutowried() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(DNAutowired.class)) {
                    continue;
                }
                String beanName = field.getAnnotation(DNAutowired.class).value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                // 访问私有，受保护的，设置权限
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void initIOC() {
        if (classes.isEmpty()) {
            return;
        }
        try {
            for (String className : classes) {
                Class<?> clazz = Class.forName(className);
                // 通过反射机制对Bean实例化
                // IOC 容器规则：
                // 1.key默认是类名首字母小
                if (clazz.isAnnotationPresent(DNController.class)) {
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    Object bean = clazz.newInstance();
                    ioc.put(beanName, bean);
                } else if (clazz.isAnnotationPresent(DNService.class)) {
                    // 2.如果用户自定义名字，则优先使用用户的自定义名字
                    DNService service = clazz.getAnnotation(DNService.class);
                    String beanName = service.value();
                    if ("".equals(beanName.trim())) {
                        beanName = lowerFirstCase(className);
                    }
                    Object bean = clazz.newInstance();
                    ioc.put(beanName, bean);

                    // 3.如果是接口，利用接口类型作为key
                    Class<?>[] interfaces = clazz.getInterfaces();

                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(), bean);
                    }
                } else {
                    continue;
                }

            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }

    }

    private String lowerFirstCase(String name) {
        if (Character.isLowerCase(name.charAt(0)))
            return name;
        else
            return (new StringBuilder()).append(Character.toLowerCase(name.charAt(0)))
                    .append(name.substring(1)).toString();
    }

    private void initScanner(String scanPackage) {
        String urlName = scanPackage.replaceAll("\\.", "/");
        URL url = this.getClass().getClassLoader()
                .getResource(urlName);
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()) {
                initScanner(urlName + "." + file.getName());
            } else {
                String className = scanPackage + "." + file.getName()
                        .replaceAll(".class", "");
                classes.add(className);
            }
        }

    }

    private void initConfig(String contextConfiguration) {
        InputStream fis = this.getClass().getClassLoader().getResourceAsStream(contextConfiguration);
        try {
            properties.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                fis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
        System.out.println("收到了get请求");
    }

    //    6.等待请求
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println("收到了post请求");
        try {
            doHandler(req, resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }


}
