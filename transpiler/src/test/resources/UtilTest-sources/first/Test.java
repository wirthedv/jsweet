package first;

import java.util.ArrayList;
import java.util.List;

public class Test  {
	
	int int1 = 1;
	int int2 = 2;
	String string1 = "a";
	String string2 = "b";
	
	void method1(String param1) {
		List<String> val1;
	}
	
	public static void main(String[] args) {
		Test var = new Test();
	}

	void plusString() {
		Object o = string1 + string2;
	}

	void plusStringInt() {
		Object o = "a" + int2;
	}

	void plusInt() {
		Object o = 1 + int2;
	}

	void minusDouble() {
		Object o = 5.0 - int2;
	}
}
