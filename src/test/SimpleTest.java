package test;

import org.junit.Test;

public class SimpleTest {


    @Test
    public void testPrint() {
        System.out.println("This is a print statement in a JUnit test.");
        // Let's force the output to show immediately
        System.out.flush();
    }
}
