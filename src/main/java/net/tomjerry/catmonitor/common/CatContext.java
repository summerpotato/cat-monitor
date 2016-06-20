package net.tomjerry.catmonitor.common;

import java.util.HashMap;
import java.util.Map;

import com.dianping.cat.Cat;

/**
 * 实现CAT上下文，以用来传递messageTreeId
 * @author kulijia
 * @date 2016-6-8
 */
public class CatContext implements Cat.Context {

	private Map<String,String> properties = new HashMap<String, String>();
	
	@Override
	public void addProperty(String key, String value) {
		properties.put(key, value);
	}

	@Override
	public String getProperty(String key) {
		return properties.get(key);
	}

}
