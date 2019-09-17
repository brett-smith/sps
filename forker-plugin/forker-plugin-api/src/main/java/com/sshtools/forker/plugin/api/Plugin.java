package com.sshtools.forker.plugin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Plugin {

	public enum StartMode {
		AUTO, DEFAULT, MANUAL
	}
	
	public boolean staticLoad() default false;

	public String[] dependencies() default {};

	public String name() default "";

	public String description() default "";

	public StartMode start() default StartMode.DEFAULT;
}
