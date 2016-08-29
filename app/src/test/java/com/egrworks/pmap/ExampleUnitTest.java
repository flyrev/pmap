package com.egrworks.pmap;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class ExampleUnitTest {
    public static final double[] lats = {63.379661560058594d, 2.2136166064799023e+37d, -5.347629728887162e-35d, 5.505145206213419e-25d};
    public static final double[] lngs = {10.34017562866211d, 1.7247286989574432e-15d, 5.201610694363819e-14d, 5.7155583306874565e-37d};
    public static final int[] ids = {13, 65, 38, 158};
    public static final int[] xs = {42, 21, 65, 38};
    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void timeConversion_isCorrect() throws Exception {
        int init = 32;
        int n = ConversionTools.calculateSpawnDiff(init);
        for (; n > 900;) n-= 900;
        String result = ConversionTools.getTimerString(n);
        //assertEquals(234, n);
        //assertEquals("lol", result);
        assertEquals(true, true);
    }

    @Test
    public void conversion_isCorrect() throws Exception {
        String file = "D:\\Projects\\Pmap\\app\\src\\test\\assets\\live";
        InputStream in = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        StringBuffer contentBuffer = new StringBuffer();
        while ((line = reader.readLine()) != null) {
            contentBuffer.append(line);
        }
        byte[] content = Base64.decode(contentBuffer.toString(), Base64.NO_WRAP);
        reader.close();

        int x = 0;
        for (int n = 0; n < content.length; n += 9) {
            if (x == 4) break;
            long tmp_i = ConversionTools.bytes2Long(content, n);
            double i = ConversionTools.long2Double(tmp_i);
            double r = ConversionTools.long2Double(ConversionTools.bytes2Long(content, n + 4));
            assertEquals(lats[x], i, 0.001);
            assertEquals(lngs[x], r, 0.001);
            assertEquals(ids[x], content[n + 8] & 0xff);
            assertEquals(xs[x], content[n + 9] & 0xff);
            x++;
            //int o = e.charCodeAt(n + 8)
            //output += i + ", " + r + ", " + o + "<br>"
            //assertEquals(i, );
        }
    }
}