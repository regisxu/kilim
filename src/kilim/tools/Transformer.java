package kilim.tools;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.List;

import kilim.analysis.ClassInfo;
import kilim.analysis.ClassWeaver;
import kilim.mirrors.Detector;
import kilim.mirrors.RuntimeClassMirrors;

public class Transformer implements ClassFileTransformer {

	private static final String[] DEFAULT_EXCLUDE_LIST = new String[] {
			"java/", "javax/", "kilim/" };

	private static Method DEFINE_CLASS_METHOD;

	static {
		try {
			DEFINE_CLASS_METHOD = ClassLoader.class.getDeclaredMethod(
					"defineClass", String.class, byte[].class, int.class,
					int.class);
			DEFINE_CLASS_METHOD.setAccessible(true);
		} catch (NoSuchMethodException e) {
			// Should never happend
			throw new RuntimeException(e);
		}
	}

	@Override
	public byte[] transform(ClassLoader loader, String className,
			Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
			byte[] classfileBuffer) throws IllegalClassFormatException {
		if (isBootstrap(loader) || isExcluded(className)) {
			return null;
		}

		System.out.println("transforming class: " + className);

		ClassInfo source = new ClassInfo(className, classfileBuffer);
		AgentClassLoader cl = new AgentClassLoader(loader);
		ClassWeaver cw = new ClassWeaver(classfileBuffer, new Detector(
				new RuntimeClassMirrors(cl)));

		cw.weave();

		List<ClassInfo> cis = cw.getClassInfos();
		cis.addAll(cl.getWeavedClasses());
		cl.clearWeavedClasses();

		ClassInfo result = null;
		for (ClassInfo info : cis) {
			if (source.className.equals(info.className)) {
				result = info;
			} else {
				try {
					System.out.println("transform extra class: "
							+ info.className);
					defineClass(info.className, info.bytes, loader);
				} catch (ClassFormatError e) {
					e.printStackTrace();
					return null;
				}
			}
		}

		System.out.println("transformed class: " + className);

		if (result == null) {
			return null;
		}
		return result.bytes;
	}

	private Class<?> defineClass(String name, byte[] bytes, ClassLoader loader)
			throws ClassFormatError {
		if (name.startsWith("kilim.")) {
			try {
				return loader.loadClass(name);
			} catch (ClassNotFoundException e) {
				// kilim class generated in runtime
			}
		}

		try {
			return (Class<?>) DEFINE_CLASS_METHOD.invoke(loader, name, bytes,
					0, bytes.length);
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	private boolean isBootstrap(ClassLoader loader) {
		return loader == null;
	}

	private boolean isExcluded(String name) {
		for (String exclude : DEFAULT_EXCLUDE_LIST) {
			if (name.startsWith(exclude)) {
				return true;
			}
		}
		return false;
	}

	public static void premain(String agentArgs, Instrumentation inst) {
		inst.addTransformer(new Transformer());
	}
}
