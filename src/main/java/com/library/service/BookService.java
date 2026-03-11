package com.library.service;

import com.library.model.Book;
import com.library.utils.FileUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

//functions including
//Task 1: get availbe book list, borrow books
//Task 2: submit books published by authors
//Task 3: get pending book list, approve/ reject books

public class BookService {
    private List<Book> books;

    //load books in the system
    public BookService() {
        this.books = FileUtils.loadBooks();
        if (this.books == null) {
            this.books = new ArrayList<>();
        }
    }

    //Task 2: submit books author published
    public boolean submitBook(Book book) {
        books.add(book);
        FileUtils.saveBooks(books);
        return true;
    }



}
