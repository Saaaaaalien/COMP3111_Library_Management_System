package com.library.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// a class that validates password, hashed password for security
//and return requirements for a secured password

public class PasswordValidator {
    // password must be at least 8 chars, 1 uppercase, 1 number
    public static boolean isValid(String password)
    {
        if(password == null || password.length() < 8) { return false; }
        boolean hasUpper = false;
        boolean hasNum = false;

        for( char c : password.toCharArray())
        {
            if(Character.isUpperCase(c)) {hasUpper = true;}
            if(Character.isDigit(c)) {hasNum = true;}
        }
        return hasUpper && hasNum;
    }
    //hash the password for security
    public static String hashPassword(String password)
    {
        try
        {
            //get the hashing algorithm SHA-256
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            //convert bytes to hexadecimal
            StringBuilder hexString = new StringBuilder();
            for(byte b : hash)
            {
                String hex = Integer.toHexString(0xff &b);
                //format each byte to ensure each byte is two hex
                if (hex.length() == 1) {hexString.append('0');}
                hexString.append(hex);
            }
            return hexString.toString();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password");
        }
    }

    public static String getRequirements() {
        return "Password must have at least 8 characters, contains 1 uppercase letter and 1 number";
    }
}

