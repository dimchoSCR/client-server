package dimcho.clientserver.history;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;


public class PluginLoader {
	
	private static final String PLUGIN_DIR = "plugins" + File.separator;
	
	public Object loadPlugin(String pluginFullClassName) throws Exception{
		try{
			// list jars
			File pluginDir = new File(PLUGIN_DIR);
			if(!pluginDir.exists()){
				throw new Exception("Missing plugin directory");
			}
			
			File[] files = pluginDir.listFiles();
			List<URL> jars = new ArrayList<URL>();
			for(File jar : files){
				if(jar.getName().endsWith(".jar")){
					jars.add(jar.toURI().toURL());
				}
			}
			
			if(jars.isEmpty()){
				throw new Exception("No plugins found in the plugin directory");
			}
			
			// load jars
			URLClassLoader loader = new URLClassLoader(jars.toArray(new URL[jars.size()]));
						
			Class<?> loadedClass = loader.loadClass(pluginFullClassName);
			Object instance = loadedClass.newInstance();
			
			loader.close();
			return instance;
			
		}catch(Exception error){
			throw new Exception("Error while loading class: " + pluginFullClassName,error);
		}
	}
}
