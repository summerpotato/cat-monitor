package net.tomjerry.catmonitor.util;

import java.util.concurrent.TimeUnit;

/**
 * 线程相关工具类
 * 
 * @author madfrog
 */
public class ThreadHelper {
	
	/**
	 * sleep等待，单位为ms，已捕捉并处理InterruptedException.
	 */
	public static void sleep(long durationMillis){
		try {
            Thread.sleep(durationMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
	}
	
	/**
     * sleep等待，已捕捉并处理InterruptedException.
     */
	public static void sleep(long duration, TimeUnit unit){
		try {
            Thread.sleep(unit.toMillis(duration));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
	}
	
}
