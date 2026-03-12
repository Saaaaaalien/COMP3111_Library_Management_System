package com.library.service;

import com.library.model.*;
import com.library.utils.FileUtils;
import com.library.utils.PasswordValidator;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

//Task 1: register student/staff
//Task 2:           author
//Task 3:           librarian

public class UserService {
    private List<User> users;

    public UserService() {
        this.users = FileUtils.loadUsers();
        if (this.users == null) {
            this.users = new ArrayList<>();
        }
    }


    // Task 2.1 Author Registration, return false if unsuccessful
    public boolean registerAuthor(String username, String fullName, String password, String bio) {
        if (findUserByUsername(username).isPresent()) {
            return false;
        }

        if (!PasswordValidator.isValid(password)) {
            return false;
        }

        String passwordHash = PasswordValidator.hashPassword(password);
        Author author = new Author(username, fullName, passwordHash, bio);
        users.add(author);
        FileUtils.saveUsers(users);
        return true;
    }

    /**
     * Legacy registration for Student/Staff used by the old LoginMenu.
     * Stores a basic User with role \"STUDENT_STAFF\".
     */
    public boolean registerStudentStaff(String username, String fullName, String password) {
        if (findUserByUsername(username).isPresent()) {
            return false;
        }
        if (!PasswordValidator.isValid(password)) {
            return false;
        }
        String passwordHash = PasswordValidator.hashPassword(password);
        User user = new User(username, fullName, passwordHash, "STUDENT_STAFF");
        users.add(user);
        FileUtils.saveUsers(users);
        return true;
    }

    /**
     * Legacy registration for Librarian used by the old LoginMenu.
     * Stores a basic User with role \"LIBRARIAN\"; employeeId is not persisted in this model.
     */
    public boolean registerLibrarian(String username, String fullName, String password, String employeeId) {
        if (findUserByUsername(username).isPresent()) {
            return false;
        }
        if (!PasswordValidator.isValid(password)) {
            return false;
        }
        String passwordHash = PasswordValidator.hashPassword(password);
        User user = new User(username, fullName, passwordHash, "LIBRARIAN");
        users.add(user);
        FileUtils.saveUsers(users);
        return true;
    }


    //login using username and password
    public Optional<User> login(String username, String password) {
        Optional<User> userOpt = findUserByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String hashedInput = PasswordValidator.hashPassword(password);
            if (user.getPasswordHash().equals(hashedInput)) {
                return userOpt;
            }
        }

        return Optional.empty();
    }

    //helper functions
    //find the user according to username
    public Optional<User> findUserByUsername(String username) {
        return users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    //see if the user is student/staff, author or librarian
    public List<User> getUsersByRole(String role) {
        return users.stream()
                .filter(u -> u.getRole().equals(role))
                .collect(Collectors.toList());
    }
}
