package com.fasterxml.jackson.util.tool.smile;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.SmileParser;

/**
 * Simple command-line utility that can be used to encode JSON as Smile, or
 * decode JSON from Smile: direction is indicated by single command-line
 * option of either "-e" (encode) or "-d" (decode).
 */
public class Tool
{
    protected final JsonFactory jsonFactory;
    protected final SmileFactory smileFactory;
    
    protected Tool()
    {
        jsonFactory = new JsonFactory();
        smileFactory = new SmileFactory();
        // check all shared refs (-> small size); add header, not trailing marker; do not use raw binary
        smileFactory.configure(SmileGenerator.Feature.CHECK_SHARED_NAMES, true);
        smileFactory.configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES, true);
        smileFactory.configure(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT, true);
        smileFactory.configure(SmileGenerator.Feature.WRITE_HEADER, true);
        smileFactory.configure(SmileGenerator.Feature.WRITE_END_MARKER, false);
        // also: do not require header
        smileFactory.configure(SmileParser.Feature.REQUIRE_HEADER, false);
    }
    
    private void process(String[] args) throws IOException
    {
        String oper = null;
        String filename = null;

        if (args.length == 2) {
            oper = args[0];
            filename = args[1];
        } else if (args.length == 1) {
            oper = args[0];
        } else {
            showUsage();
        }
            
        boolean encode = "-e".equals(oper);
        if (encode) {
            encode(inputStream(filename));
        } else if ("-d".equals(oper)) {
            decode(inputStream(filename));
        } else if ("-v".equals(oper)) {
            // need to read twice (encode, verify/compare)
            verify(inputStream(filename), inputStream(filename));
        } else {
            showUsage();
        }
    }

    private InputStream inputStream(String filename) throws IOException
    {
        // if no argument given, read from stdin
        if (filename == null) {
            return System.in;
        }
        File src = new File(filename);
        if (!src.exists()) {
            System.err.println("File '"+filename+"' does not exist.");
            System.exit(1);
        }
        return new FileInputStream(src);
    }
    
    private void decode(InputStream in) throws IOException
    {
        JsonParser p = smileFactory.createParser(in);
        JsonGenerator g = jsonFactory.createGenerator(System.out, JsonEncoding.UTF8);

        while (true) {
            /* Just one trick: since Smile can have segments (multiple 'documents' in output
             * stream), we should not stop at first end marker, only bail out if two are seen
             */
            if (p.nextToken() == null) {
                if (p.nextToken() == null) {
                    break;
                }
            }
            g.copyCurrentEvent(p);
        }
        p.close();
        g.close();
    }        

    private void encode(InputStream in) throws IOException
    {
        JsonParser p = jsonFactory.createParser(in);
        JsonGenerator g = smileFactory.createGenerator(System.out, JsonEncoding.UTF8);
        while ((p.nextToken()) != null) {
            g.copyCurrentEvent(p);
        }
        p.close();
        g.close();
    }

    @SuppressWarnings("resource")
    private void verify(InputStream in, InputStream in2) throws IOException
    {
        JsonParser p = jsonFactory.createParser(in);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4000);
        JsonGenerator g = smileFactory.createGenerator(bytes, JsonEncoding.UTF8);

        // First, read, encode in memory buffer
        while ((p.nextToken()) != null) {
            g.copyCurrentEvent(p);
        }
        p.close();
        g.close();

        // and then re-read both, verify
        p = jsonFactory.createParser(in2);
        byte[] smile = bytes.toByteArray();
        JsonParser p2 = smileFactory.createParser(smile);

        JsonToken t;
        int count = 0;
        while ((t = p.nextToken()) != null) {
            JsonToken t2 = p2.nextToken();
            ++count;
            if (t != t2) {
                throw new IOException("Input and encoded differ, token #"+count+"; expected "+t+", got "+t2);
            }
            // also, need to have same texts...
            String text1 = p.getText();
            String text2 = p2.getText();
            if (!text1.equals(text2)) {
                throw new IOException("Input and encoded differ, token #"+count+"; expected text '"+text1+"', got '"+text2+"'");
            }
        }

        System.out.println("OK: verified "+count+" tokens (from "+smile.length+" bytes of Smile encoded data), input and encoded contents are identical");
    }
    
    protected void showUsage()
    {
        System.err.println("Usage: java "+getClass().getName()+" -e/-d [file]");
        System.err.println(" (if no file given, reads from stdin -- always writes to stdout)");
        System.err.println(" -d: decode Smile encoded input as JSON");
        System.err.println(" -e: encode JSON (text) input as Smile");
        System.err.println(" -v: encode JSON (text) input as Smile; read back, verify, do not write out");
        System.exit(1);        
    }

    public static void main(String[] args) throws IOException {
        new Tool().process(args);
    }

}
