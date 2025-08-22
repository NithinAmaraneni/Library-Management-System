import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;

// ======================= DOMAIN & PERSISTENCE =======================

// Book class
class Book implements Serializable {
    private int id;
    private String title;
    private String author;
    private String genre;
    private boolean isIssued;

    public Book(int id, String title, String author, String genre) {
        this.id = id;
        this.title = title.trim();
        this.author = author.trim();
        this.genre = genre.trim();
        this.isIssued = false;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getGenre() { return genre; }
    public boolean isIssued() { return isIssued; }
    public void setIssued(boolean issued) { this.isIssued = issued; }

    @Override
    public String toString() {
        return id + " | " + title + " | " + author + " | " + genre + " | " +
                (isIssued ? "Issued" : "Available");
    }
}

// BorrowRecord class
class BorrowRecord implements Serializable {
    private int bookId;
    private LocalDate issueDate;
    private LocalDate dueDate;

    public BorrowRecord(int bookId) {
        this.bookId = bookId;
        this.issueDate = LocalDate.now();
        this.dueDate = issueDate.plusDays(7);
    }

    public int getBookId() { return bookId; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }

    @Override
    public String toString() {
        return "BookID: " + bookId + " | Issued: " + issueDate + " | Due: " + dueDate;
    }
}

// User class
class User implements Serializable {
    private int userId;
    private String name;
    private String username;
    private String password;
    private String role; // "admin" or "user"
    private ArrayList<BorrowRecord> borrowedBooks;

    public User(int userId, String name, String username, String password, String role) {
        this.userId = userId;
        this.name = name.trim();
        this.username = username.trim();
        this.password = password; // (optional: hash later)
        this.role = role;
        this.borrowedBooks = new ArrayList<>();
    }

    public int getUserId() { return userId; }
    public String getName() { return name; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getRole() { return role; }
    public ArrayList<BorrowRecord> getBorrowedBooks() { return borrowedBooks; }

    public void borrowBook(int bookId) { borrowedBooks.add(new BorrowRecord(bookId)); }

    public BorrowRecord returnBook(int bookId) {
        for (BorrowRecord br : borrowedBooks) {
            if (br.getBookId() == bookId) {
                borrowedBooks.remove(br);
                return br;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return userId + " | " + name + " | @" + username + " | Role: " + role + " | Borrowed: " + borrowedBooks;
    }
}

// Library service with persistence
class Library {
    private static final String BOOKS_FILE = "books.dat";
    private static final String USERS_FILE = "users.dat";
    private static final int FINE_PER_DAY = 10;

    ArrayList<Book> books = new ArrayList<>();
    ArrayList<User> users = new ArrayList<>();

    public Library() {
        loadBooks();
        loadUsers();
        ensureAdminExists();
    }

    // ---- Admin bootstrap
    private void ensureAdminExists() {
        boolean adminExists = users.stream().anyMatch(u -> "admin".equals(u.getRole()));
        if (!adminExists) {
            int nextId = nextUserId();
            User admin = new User(nextId, "Administrator", "admin", "admin123", "admin");
            users.add(admin);
            saveUsers();
        }
    }

    // ---- User management
    public synchronized String registerUser(String name, String username, String password) {
        if (users.stream().anyMatch(u -> u.getUsername().equalsIgnoreCase(username))) {
            return "Username already exists.";
        }
        int id = nextUserId();
        users.add(new User(id, name, username, password, "user"));
        saveUsers();
        return "User registered successfully. Your User ID: " + id;
    }

    public synchronized User loginUser(String username, String password) {
        for (User u : users) {
            if (u.getUsername().equals(username) && u.getPassword().equals(password)) {
                return u;
            }
        }
        return null;
    }

    private int nextUserId() {
        return users.stream().map(User::getUserId).max(Comparator.naturalOrder()).orElse(0) + 1;
    }

    // ---- Book management
    public synchronized String addBook(Integer maybeId, String title, String author, String genre) {
        int id = (maybeId == null)
                ? nextBookId()
                : maybeId;
        if (getBookById(id) != null) {
            return "Book ID already exists.";
        }
        books.add(new Book(id, title, author, genre));
        saveBooks();
        return "Book added with ID: " + id;
    }

    private int nextBookId() {
        return books.stream().map(Book::getId).max(Comparator.naturalOrder()).orElse(0) + 1;
    }

    public synchronized String removeBook(int bookId) {
        boolean removed = books.removeIf(b -> b.getId() == bookId);
        if (removed) {
            saveBooks();
            return "Book removed.";
        }
        return "Book not found.";
    }

    public synchronized String borrowBook(User user, int bookId) {
        Book book = getBookById(bookId);
        if (book == null) return "Book not found.";
        if (book.isIssued()) return "Book is already issued.";
        book.setIssued(true);
        user.borrowBook(bookId);
        saveBooks();
        saveUsers();
        LocalDate due = LocalDate.now().plusDays(7);
        return "Borrowed: " + book.getTitle() + " | Due: " + due;
    }

    public synchronized String returnBook(User user, int bookId) {
        Book book = getBookById(bookId);
        if (book == null) return "Book not found.";
        if (!book.isIssued()) return "This book is not issued.";
        BorrowRecord rec = user.returnBook(bookId);
        if (rec == null) return "This user hasn't borrowed that book.";
        long daysLate = ChronoUnit.DAYS.between(rec.getDueDate(), LocalDate.now());
        book.setIssued(false);
        saveBooks();
        saveUsers();
        if (daysLate > 0) {
            long fine = daysLate * FINE_PER_DAY;
            return "Returned late by " + daysLate + " day(s). Fine = ₹" + fine;
        }
        return "Returned on time. No fine.";
    }

    public synchronized String viewBooksAsString() {
        if (books.isEmpty()) return "No books available.";
        StringBuilder sb = new StringBuilder();
        books.stream().sorted(Comparator.comparing(Book::getId)).forEach(b -> sb.append(b).append("\n"));
        return sb.toString();
    }

    public synchronized String viewUsersAsString() {
        if (users.isEmpty()) return "No users registered.";
        StringBuilder sb = new StringBuilder();
        users.stream().sorted(Comparator.comparing(User::getUserId)).forEach(u -> sb.append(u).append("\n"));
        return sb.toString();
    }

    public synchronized String searchBooksAsString(String keyword) {
        String k = keyword.toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (Book b : books) {
            if (b.getTitle().toLowerCase().contains(k) ||
                b.getAuthor().toLowerCase().contains(k) ||
                b.getGenre().toLowerCase().contains(k)) {
                sb.append(b).append("\n");
            }
        }
        return sb.length() == 0 ? "No books found for: " + keyword : sb.toString();
    }

    public synchronized String viewBorrowedOf(User user) {
        if (user.getBorrowedBooks().isEmpty()) return "No borrowed books.";
        StringBuilder sb = new StringBuilder();
        for (BorrowRecord br : user.getBorrowedBooks()) {
            Book b = getBookById(br.getBookId());
            String title = (b == null) ? "(unknown)" : b.getTitle();
            sb.append("ID: ").append(br.getBookId())
              .append(" | ").append(title)
              .append(" | Issued: ").append(br.getIssueDate())
              .append(" | Due: ").append(br.getDueDate())
              .append("\n");
        }
        return sb.toString();
    }

    private Book getBookById(int bookId) {
        for (Book b : books) if (b.getId() == bookId) return b;
        return null;
    }

    // ---- Persistence
    private void saveBooks() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(BOOKS_FILE))) {
            oos.writeObject(books);
        } catch (IOException ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void loadBooks() {
        File f = new File(BOOKS_FILE);
        if (f.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                books = (ArrayList<Book>) ois.readObject();
            } catch (Exception ignored) {}
        }
    }

    private void saveUsers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(USERS_FILE))) {
            oos.writeObject(users);
        } catch (IOException ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void loadUsers() {
        File f = new File(USERS_FILE);
        if (f.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                users = (ArrayList<User>) ois.readObject();
            } catch (Exception ignored) {}
        }
    }
}

// ======================= GUI (Swing) =======================

public class LibraryApp {
    // Shared library instance across all windows
    private static final Library LIB = new Library();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginFrame::new);
    }

    // ------------- Login & Register -------------
    static class LoginFrame extends JFrame {
        private final JTextField usernameField = new JTextField();
        private final JPasswordField passwordField = new JPasswordField();

        LoginFrame() {
            super("Library - Login");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(420, 220);
            setLayout(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 8, 6, 8);
            c.fill = GridBagConstraints.HORIZONTAL;

            JLabel uLbl = new JLabel("Username:");
            JLabel pLbl = new JLabel("Password:");
            JButton loginBtn = new JButton("Login");
            JButton regBtn = new JButton("Register");

            c.gridx = 0; c.gridy = 0; add(uLbl, c);
            c.gridx = 1; c.gridy = 0; add(usernameField, c);
            c.gridx = 0; c.gridy = 1; add(pLbl, c);
            c.gridx = 1; c.gridy = 1; add(passwordField, c);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            btns.add(loginBtn);
            btns.add(regBtn);
            c.gridx = 0; c.gridy = 2; c.gridwidth = 2; add(btns, c);

            loginBtn.addActionListener(e -> doLogin());
            regBtn.addActionListener(e -> new RegisterDialog(this));

            setLocationRelativeTo(null);
            setVisible(true);
        }

        private void doLogin() {
            String u = usernameField.getText().trim();
            String p = new String(passwordField.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter username and password.");
                return;
            }
            User logged = LIB.loginUser(u, p);
            if (logged == null) {
                JOptionPane.showMessageDialog(this, "Invalid credentials.");
                return;
            }
            JOptionPane.showMessageDialog(this, "Welcome, " + logged.getName() + " (" + logged.getRole() + ")");
            dispose();
            if ("admin".equals(logged.getRole())) new AdminFrame(logged);
            else new UserFrame(logged);
        }
    }

    static class RegisterDialog extends JDialog {
        RegisterDialog(JFrame owner) {
            super(owner, "Register New User", true);
            setSize(420, 260);
            setLayout(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 8, 6, 8);
            c.fill = GridBagConstraints.HORIZONTAL;

            JTextField name = new JTextField();
            JTextField uname = new JTextField();
            JPasswordField pass = new JPasswordField();

            c.gridx = 0; c.gridy = 0; add(new JLabel("Full Name:"), c);
            c.gridx = 1; c.gridy = 0; add(name, c);
            c.gridx = 0; c.gridy = 1; add(new JLabel("Username:"), c);
            c.gridx = 1; c.gridy = 1; add(uname, c);
            c.gridx = 0; c.gridy = 2; add(new JLabel("Password:"), c);
            c.gridx = 1; c.gridy = 2; add(pass, c);

            JButton save = new JButton("Register");
            c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
            JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            p.add(save);
            add(p, c);

            save.addActionListener(ae -> {
                String nm = name.getText().trim();
                String un = uname.getText().trim();
                String pw = new String(pass.getPassword());
                if (nm.isEmpty() || un.isEmpty() || pw.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "All fields are required.");
                    return;
                }
                String msg = LIB.registerUser(nm, un, pw);
                JOptionPane.showMessageDialog(this, msg);
                if (!msg.toLowerCase().contains("exists")) dispose();
            });

            setLocationRelativeTo(owner);
            setVisible(true);
        }
    }

    // ------------- Admin Dashboard -------------
    static class AdminFrame extends JFrame {
        private final JTextArea output = new JTextArea(16, 60);

        AdminFrame(User admin) {
            super("Admin Dashboard - " + admin.getName());
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(700, 520);
            setLayout(new BorderLayout(8, 8));

            // Top controls
            JPanel top = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 6, 4, 6);
            c.fill = GridBagConstraints.HORIZONTAL;

            JTextField idField = new JTextField(20); // optional
            JTextField titleField = new JTextField(20);
            JTextField authorField = new JTextField(20);
            JTextField genreField = new JTextField(20);

            c.gridx=0;c.gridy=0; top.add(new JLabel("Book ID (optional):"), c);
            c.gridx=1;c.gridy=0; top.add(idField, c);
            c.gridx=0;c.gridy=1; top.add(new JLabel("Title:"), c);
            c.gridx=1;c.gridy=1; top.add(titleField, c);
            c.gridx=0;c.gridy=2; top.add(new JLabel("Author:"), c);
            c.gridx=1;c.gridy=2; top.add(authorField, c);
            c.gridx=0;c.gridy=3; top.add(new JLabel("Genre:"), c);
            c.gridx=1;c.gridy=3; top.add(genreField, c);

            JButton addBtn = new JButton("Add Book");
            JButton removeBtn = new JButton("Remove by ID");
            JTextField removeId = new JTextField();
            JButton viewBooksBtn = new JButton("View Books");
            JButton viewUsersBtn = new JButton("View Users");
            JButton logoutBtn = new JButton("Logout");

            JPanel btns = new JPanel(new GridLayout(2,3,6,6));
            btns.add(addBtn);
            btns.add(viewBooksBtn);
            btns.add(viewUsersBtn);
            btns.add(new JLabel("Remove ID:"));
            btns.add(removeId);
            btns.add(removeBtn);

            JPanel north = new JPanel(new BorderLayout());
            north.add(top, BorderLayout.CENTER);
            north.add(btns, BorderLayout.SOUTH);

            // Output area
            output.setEditable(false);
            JScrollPane scroll = new JScrollPane(output);

            add(north, BorderLayout.NORTH);
            add(scroll, BorderLayout.CENTER);
            add(logoutBtn, BorderLayout.SOUTH);

            addBtn.addActionListener(e -> {
                Integer id = null;
                String idText = idField.getText().trim();
                if (!idText.isEmpty()) {
                    try { id = Integer.parseInt(idText); } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(this, "Invalid ID"); return;
                    }
                }
                String t = titleField.getText().trim();
                String a = authorField.getText().trim();
                String g = genreField.getText().trim();
                if (t.isEmpty() || a.isEmpty() || g.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Fill Title, Author, Genre.");
                    return;
                }
                String msg = LIB.addBook(id, t, a, g);
                output.setText(msg + "\n\n" + LIB.viewBooksAsString());
                idField.setText(""); titleField.setText(""); authorField.setText(""); genreField.setText("");
            });

            removeBtn.addActionListener(e -> {
                try {
                    int id = Integer.parseInt(removeId.getText().trim());
                    String msg = LIB.removeBook(id);
                    output.setText(msg + "\n\n" + LIB.viewBooksAsString());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Enter a valid numeric ID.");
                }
            });

            viewBooksBtn.addActionListener(e -> output.setText(LIB.viewBooksAsString()));
            viewUsersBtn.addActionListener(e -> output.setText(LIB.viewUsersAsString()));

            logoutBtn.addActionListener(e -> {
                dispose();
                SwingUtilities.invokeLater(LoginFrame::new);
            });

            setLocationRelativeTo(null);
            setVisible(true);
        }
    }

    // ------------- User Dashboard -------------
    static class UserFrame extends JFrame {
        private final JTextArea output = new JTextArea(16, 60);
        private final User me;

        UserFrame(User user) {
            super("User Dashboard - " + user.getName());
            this.me = user;
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setSize(700, 520);
            setLayout(new BorderLayout(8, 8));

            JPanel top = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 6, 4, 6);
            c.fill = GridBagConstraints.HORIZONTAL;

            JTextField searchField = new JTextField(20);
            JTextField borrowIdField = new JTextField(20);
            JTextField returnIdField = new JTextField(20);

            c.gridx=0;c.gridy=0; top.add(new JLabel("Search (title/author/genre):"), c);
            c.gridx=1;c.gridy=0; top.add(searchField, c);
            c.gridx=0;c.gridy=1; top.add(new JLabel("Borrow Book ID:"), c);
            c.gridx=1;c.gridy=1; top.add(borrowIdField, c);
            c.gridx=0;c.gridy=2; top.add(new JLabel("Return Book ID:"), c);
            c.gridx=1;c.gridy=2; top.add(returnIdField, c);

            JButton searchBtn = new JButton("Search");
            JButton borrowBtn = new JButton("Borrow");
            JButton returnBtn = new JButton("Return");
            JButton viewMineBtn = new JButton("View My Borrowed");
            JButton viewAllBtn = new JButton("View All Books");
            JButton logoutBtn = new JButton("Logout");

            JPanel btns = new JPanel(new GridLayout(2,3,6,6));
            btns.add(searchBtn);
            btns.add(borrowBtn);
            btns.add(returnBtn);
            btns.add(viewMineBtn);
            btns.add(viewAllBtn);
            btns.add(logoutBtn);

            JPanel north = new JPanel(new BorderLayout());
            north.add(top, BorderLayout.CENTER);
            north.add(btns, BorderLayout.SOUTH);

            output.setEditable(false);
            JScrollPane scroll = new JScrollPane(output);

            add(north, BorderLayout.NORTH);
            add(scroll, BorderLayout.CENTER);

            searchBtn.addActionListener(e -> {
                String k = searchField.getText().trim();
                if (k.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter a keyword."); return; }
                output.setText(LIB.searchBooksAsString(k));
            });

            viewAllBtn.addActionListener(e -> output.setText(LIB.viewBooksAsString()));

            viewMineBtn.addActionListener(e -> output.setText(LIB.viewBorrowedOf(me)));

            borrowBtn.addActionListener(e -> {
                try {
                    int id = Integer.parseInt(borrowIdField.getText().trim());
                    String msg = LIB.borrowBook(me, id);
                    output.setText(msg + "\n\n" + LIB.viewBorrowedOf(me));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Enter a valid numeric Book ID to borrow.");
                }
            });

            returnBtn.addActionListener(e -> {
                try {
                    int id = Integer.parseInt(returnIdField.getText().trim());
                    String msg = LIB.returnBook(me, id);
                    output.setText(msg + "\n\n" + LIB.viewBorrowedOf(me));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Enter a valid numeric Book ID to return.");
                }
            });

            logoutBtn.addActionListener(e -> {
                dispose();
                SwingUtilities.invokeLater(LoginFrame::new);
            });

            setLocationRelativeTo(null);
            setVisible(true);
        }
    }
}
