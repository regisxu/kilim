package kilim.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import kilim.analysis.ClassInfo;

public class AgentClassLoader extends ClassLoader {

	private Weaver weaver;

	private List<ClassInfo> weaved = new LinkedList<ClassInfo>();

	public AgentClassLoader(ClassLoader parent) {
		super(parent);
		weaver = new Weaver(this);
	}

	protected synchronized Class<?> loadClass(String name, boolean resolve)
			throws ClassNotFoundException {
		if (name.startsWith("java.") || name.startsWith("javax.")
				|| name.startsWith("kilim.")) {
			return super.loadClass(name, resolve);
		}

		// First, check if the class has already been loaded
		System.out.println("findLoadedClass: " + name);
		Class<?> c = findLoadedClass(name);
		if (c == null) {
			c = findClass(name);
		} else {
			System.out.println("find loaded class: " + name);
		}
		if (resolve) {
			resolveClass(c);
		}
		return c;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		Class<?> ret = null;
		String classFileName = name.replace('.', '/') + ".class";
		InputStream in = getParent().getResourceAsStream(classFileName);
		if (in == null) {
			throw new ClassNotFoundException(name);
		}

		byte[] code = null;
		try {
			code = readClass(in);
		} catch (IOException ignore) {
			System.err.println(ignore.getMessage());
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				// ignore;
			}
		}

		List<ClassInfo> cis = weaver.weave(new ClassInfo(name, code));
		weaved.addAll(cis);
		for (ClassInfo ci : cis) {
			if (findLoadedClass(ci.className) != null)
				continue;
			Class<?> c = defineClass(ci.className, ci.bytes, 0, ci.bytes.length);
			if (ci.className.equals(name)) {
				ret = c;
			} else {
				// extra classes produced by the weaver. resolve them
				// right away
				// That way, when the given class name is resolved,
				// it'll find its
				// kilim related state object classes right away.
				if (ci.className.startsWith("kilim.S")) {
					resolveClass(c);
				}
			}
		}
		if (ret == null) {
			// code exists, but didn't need to be woven
			ret = getParent().loadClass(name);
		}
		if (ret == null) {
			throw new ClassNotFoundException(name);
		}
		return ret;
	}

	public List<ClassInfo> getWeavedClasses() {
		return Collections.unmodifiableList(weaved);
	}

	public void clearWeavedClasses() {
		weaved.clear();
	}

	private static byte[] readClass(InputStream fin) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] bs = new byte[1024];
		try {
			int count = 0;
			while ((count = fin.read(bs)) != -1) {
				out.write(bs, 0, count);
			}
		} finally {
			try {
				out.close();
			} catch (IOException e) {
				// ignore
			}
		}
		return out.toByteArray();
	}
}
