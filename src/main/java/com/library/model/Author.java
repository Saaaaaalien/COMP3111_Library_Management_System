package com.library.model;


//Task 2.1 Author data upon registration
//serializable save object to a file
public class Author extends User {

    private static final long serialVersionUID = 1L;
    //other information is in parent
    //Bio (optional)
    private String bio;

    //Constructor to create an com.library.model.Author during registration
    public Author(String username, String fullName, String passwordHash, String bio) {
        super(username, fullName, passwordHash, "Author");
        this.bio = bio;
    }

    public String getBio() {
        return bio;
    }
    public void setBio(String bio){this.bio = bio;}
}
