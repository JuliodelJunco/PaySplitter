package com.example.paySplitter.Controller;

import android.os.Build;

import com.example.paySplitter.Model.Balance;
import com.example.paySplitter.Model.Currency;
import com.example.paySplitter.Model.Debt;
import com.example.paySplitter.Model.Expense;
import com.example.paySplitter.Model.Group;
import com.example.paySplitter.Model.User;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class APIController {
    //Singleton implementation for APIController
    private static APIController instance;
    private Drive driveService;
    private User user;
    private final Map<String,JSONObject> metadatas = new HashMap<>();
    //set the API to use
    public void setAPIController(Drive driveService, User user) {
        this.driveService = driveService;
        this.user = user;
    }
    public static synchronized APIController getInstance() {
        if (instance == null) {
            instance = new APIController();
        }
        return instance;
    }
    public User getUser() {
        return user;
    }
    //Gets the group names in the PaySplitter folder and returns them as a list of Group objects
    public ArrayList<Group> loadGroupNames() {
        ArrayList<Group> groups = new ArrayList<>();
        try {
            String appFolderId = getOrCreateFolder();
            HashSet<String> groupFolders = listFoldersInFolder(appFolderId);

            for (String folderId : groupFolders) {

                // Checking for shortcut folders
                File folderMeta = driveService.files().get(folderId)
                        .setFields("id, mimeType, shortcutDetails/targetId")
                        .execute();
                // Get the target ID if it's a shortcut
                if ("application/vnd.google-apps.shortcut".equals(folderMeta.getMimeType())) {
                    folderId = folderMeta.getShortcutDetails().getTargetId(); // resolver target real
                }
                File metadataFile = findFileInFolder(folderId, "metadata.json");


                assert metadataFile != null;
                String metadataJson = downloadFileContent(metadataFile.getId());
                assert metadataJson != null;
                JSONObject metadata = new JSONObject(metadataJson);
                metadatas.put(folderId, metadata);
                // Only shows groups that the user is in
                JSONArray participantsJson = metadata.getJSONArray("participants");
                boolean valid = false;
                for (int i = 0; i < participantsJson.length(); i++) {
                    JSONObject p = participantsJson.getJSONObject(i);
                    valid = p.getString("gmail").equals(user.getGmail());
                    if(valid){
                        break;
                    }
                }
                if (valid) {

                    Group group = new Group();
                    group.setName(metadata.getString("name"));
                    group.setId(folderId);
                    groups.add(group);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return groups;
    }
    //Gets the group with the given id and returns it as a Group object
    public Group loadGroup(String folderId,boolean newUser){
        try {
            File metadataFile = findFileInFolder(folderId, "metadata.json");
            if (metadataFile == null) throw new RuntimeException("Metadata file not found");

            String metadataJson = downloadFileContent(metadataFile.getId());
            assert metadataJson != null;
            JSONObject metadata = new JSONObject(metadataJson);

            JSONArray participantsJson = metadata.getJSONArray("participants");
            ArrayList<User> participants = new ArrayList<>();
            boolean valid = false;
            for (int i = 0; i < participantsJson.length(); i++) {
                JSONObject p = participantsJson.getJSONObject(i);
                User u = new User();
                u.setName(p.getString("name"));
                u.setGmail(p.getString("gmail"));
                if(!valid) valid = u.getGmail().equals(user.getGmail());
                if(newUser) newUser = !u.getGmail().equals(user.getGmail());
                participants.add(u);
            }
            // Only shows groups that the user is in
            if(!valid&&!newUser){
                throw new RuntimeException("User not in group");
            }else if(newUser){
                File shortcutMetadata = new File();
                shortcutMetadata.setName(metadata.getString("name"));
                shortcutMetadata.setMimeType("application/vnd.google-apps.shortcut");
                shortcutMetadata.setParents(Collections.singletonList(getOrCreateFolder()));
                shortcutMetadata.setShortcutDetails(new File.ShortcutDetails().setTargetId(folderId));

                driveService.files().create(shortcutMetadata).setFields("id").execute();
                participants.add(user);
                JSONObject p = new JSONObject();
                p.put("name", user.getName());
                p.put("gmail", user.getGmail());
                ArrayList<User> participantsList = loadUsersFromArray(participantsJson);
                Set<User> participantSet = new HashSet<>(participantsList);
                if(!participantSet.contains(user)) participantsJson.put(p);
                metadata.put("participants", participantsJson);
                uploadFileContent(metadataFile.getId(), metadata.toString());
            }
            // Set the group
            Group group = new Group();
            group.setName(metadata.getString("name"));
            group.setCurrency(Currency.valueOf(metadata.getString("currency")));
            group.setParticipants(participants);
            group.setId(folderId);

            if (metadata.has("debts")) {
                JSONArray debtsJson = metadata.getJSONArray("debts");
                ArrayList<Debt> debts = new ArrayList<>();
                for (int i = 0; i < debtsJson.length(); i++) {
                    JSONObject d = debtsJson.getJSONObject(i);
                    User creditor = findUserByGmail(participants, d.getString("creditor"));
                    User debtor = findUserByGmail(participants, d.getString("debtor"));
                    debts.add(new Debt(creditor, debtor, d.getDouble("amount")));
                }
                group.setDebts(debts);
            }

            if (metadata.has("balances")) {
                JSONArray balancesJson = metadata.getJSONArray("balances");
                Map<User, Balance> balances = new HashMap<>();
                for (int i = 0; i < balancesJson.length(); i++) {
                    JSONObject b = balancesJson.getJSONObject(i);
                    User u = findUserByGmail(participants, b.getString("gmail"));
                    Balance balance = new Balance();
                    balance.setAmount(b.getDouble("amount"));
                    balance.setInDebt(b.getBoolean("inDebt"));
                    balance.setUser(u);
                    balances.put(u, balance);
                }
                group.setBalances(balances);
            }

            if (metadata.has("expenses")) {
                JSONArray expensesJson = metadata.getJSONArray("expenses");
                ArrayList<Expense> expenses = new ArrayList<>();
                for (int i = 0; i < expensesJson.length(); i++) {
                    JSONObject ex = expensesJson.getJSONObject(i);
                    Expense expense = new Expense();
                    expense.setName(ex.getString("name"));
                    expense.setAmount(ex.getDouble("amount"));
                    expenses.add(expense);
                }
                group.setExpenses(expenses);
            }
            return group;
        }catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    //Gets the expense with the given name and returns it as a Expense object
    public Expense loadExpense(Group group, String expenseName){
        try {
            String folderId = group.getId();
            ArrayList<User> participants = group.getParticipants();
            File file = findFileInFolder(folderId, expenseName + ".expense.json");
            assert file != null;
            String jsonContent = downloadFileContent(file.getId());
            assert jsonContent != null;
            JSONObject e = new JSONObject(jsonContent);
            // Set the expense
            Expense expense = new Expense();
            expense.setName(e.getString("name"));
            expense.setAmount(e.getDouble("amount"));
            expense.setParticipants(loadUsersFromArray(e.getJSONArray("participants")));

            Map<User, Double> creditors = new HashMap<>();
            JSONObject cred = e.getJSONObject("creditors");
            for (Iterator<String> it = cred.keys(); it.hasNext(); ) {
                String key = it.next();
                User u = findUserByGmail(participants, key);
                creditors.put(u, cred.getJSONObject(key).getDouble("amount"));
            }
            expense.setCreditors(creditors);

            Map<User, Double> debtors = new HashMap<>();
            JSONObject debt = e.getJSONObject("debtors");
            for (Iterator<String> it = debt.keys(); it.hasNext(); ) {
                String key = it.next();
                User u = findUserByGmail(participants, key);
                debtors.put(u, debt.getJSONObject(key).getDouble("amount"));
            }
            expense.setDebtors(debtors);
            return expense;
        }catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    // Deletes the group with the given id
    public void deleteGroup(Group group) {
        try {
            String folderId = group.getId();
            driveService.files().delete(folderId).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Adds a new group to the PaySplitter folder
    public void addGroup(Group newGroup) {
        try {
            String appFolderId = getOrCreateFolder();

            File groupFolderMetadata = new File();
            groupFolderMetadata.setName(newGroup.getName());
            groupFolderMetadata.setMimeType("application/vnd.google-apps.folder");
            groupFolderMetadata.setParents(Collections.singletonList(appFolderId));

            File groupFolder = driveService.files().create(groupFolderMetadata).setFields("id").execute();
            String groupFolderId = groupFolder.getId();

            newGroup.setId(groupFolderId);

            Permission permission = new Permission()
                .setType("anyone")
                .setRole("writer");
            driveService.permissions().create(groupFolderId, permission)
                    .setSendNotificationEmail(false)
                    .execute();

            JSONObject metadata = new JSONObject();
            metadata.put("name", newGroup.getName());
            metadata.put("currency", newGroup.getCurrency().name());

            JSONArray participantArray = new JSONArray();
            for (User u : newGroup.getParticipants()) {
                JSONObject p = new JSONObject();
                p.put("name", u.getName());
                p.put("gmail", u.getGmail());
                participantArray.put(p);
            }

            metadata.put("participants", participantArray);
            metadata.put("debts", new JSONArray());
            metadata.put("balances", new JSONArray());
            metadata.put("expenses", new JSONArray());
            metadatas.put(groupFolderId, metadata);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(metadata.toString().getBytes(StandardCharsets.UTF_8));

            File metaFile = new File();
            metaFile.setName("metadata.json");
            metaFile.setParents(Collections.singletonList(groupFolderId));

            com.google.api.client.http.AbstractInputStreamContent content =
                    new com.google.api.client.http.ByteArrayContent("application/json", outputStream.toByteArray());

            driveService.files().create(metaFile, content).setFields("id").execute();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Sets the group with the given id
    public void setGroup(Group group) {
        try {
            String folderId = group.getId();

            JSONObject metadata = metadatas.get(folderId);
            assert metadata != null;
            metadata.put("name", group.getName());
            metadata.put("currency", group.getCurrency().name());
            //Check change in participants and call setParticipants if needed
            if(group.getParticipants().size() < metadata.getJSONArray("participants").length()){
                setParticipants(folderId,group);
            }else{
                File metadataFile = findFileInFolder(folderId, "metadata.json");
                if (metadataFile != null) {
                    uploadFileContent(metadataFile.getId(), metadata.toString());
                }
                metadatas.put(folderId, metadata);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Sets the participants of the group
    public void setParticipants(String folderId, Group group) {
        try {


            JSONObject metadata = metadatas.get(folderId);

            JSONArray participantArray = new JSONArray();
            for (User u : group.getParticipants()) {
                JSONObject p = new JSONObject();
                p.put("name", u.getName());
                p.put("gmail", u.getGmail());
                participantArray.put(p);
            }
            // Check change in participants and call setExpenses if needed
            assert metadata != null;
            if(participantArray.length() < metadata.getJSONArray("participants").length()){
                metadata.put("participants", participantArray);
                setExpenses(folderId,group.getDebts(),group.getBalances(),group.getExpenses());
            }else{
                metadata.put("participants", participantArray);
                File metadataFile = findFileInFolder(folderId, "metadata.json");
                if (metadataFile != null) {
                    uploadFileContent(metadataFile.getId(), metadata.toString());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Sets the expenses of the group
    public void setExpenses(String folderId,ArrayList<Debt> debts, Map<User, Balance> balances, ArrayList<Expense> expenses) {
        try {

            JSONObject metadata = metadatas.get(folderId);

            JSONArray debtArray = new JSONArray();
            if (debts != null) {
                debts.forEach(debt -> {
                    JSONObject d = new JSONObject();
                    try {
                        d.put("creditor", debt.getCreditor().getGmail());
                        d.put("debtor", debt.getDebtor().getGmail());
                        d.put("amount", debt.getAmount());
                        debtArray.put(d);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            assert metadata != null;
            metadata.put("debts", debtArray);

            JSONArray balancesArray = new JSONArray();
            if (balances != null) {
                balances.forEach((user, balance) -> {
                    JSONObject b = new JSONObject();
                    try {
                        b.put("gmail", user.getGmail());
                        b.put("amount", balance.getAmount());
                        b.put("inDebt", balance.isInDebt());
                        balancesArray.put(b);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            metadata.put("balances", balancesArray);

            JSONArray expensesArray = new JSONArray();
            if (expenses != null) {
                expenses.forEach((expense) -> {
                    JSONObject ex = new JSONObject();
                    try {
                        ex.put("name", expense.getName());
                        ex.put("amount", expense.getAmount());
                        expensesArray.put(ex);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            metadata.put("expenses", expensesArray);


            File metadataFile = findFileInFolder(folderId, "metadata.json");
            if (metadataFile != null) {
                uploadFileContent(metadataFile.getId(), metadata.toString());
            }

            // Delete old expenses
            File owner = driveService.files().get(folderId).setFields("owners").execute();
            String ownerEmail = owner.getOwners().get(0).getEmailAddress();
            if (ownerEmail.equals(user.getGmail())) {
                for (File file : listFilesInFolder(folderId)) {
                    if (file.getName().endsWith(".expense.json") && !expenses.contains(file.getName().replaceFirst("\\.expense\\.json$", ""))) {
                        driveService.files().delete(file.getId()).execute();
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Set a specific expense
    public void setExpense(String folderId, ArrayList<Debt> debts, Map<User, Balance> balances, Expense expense) {
        try {

            JSONObject metadata = metadatas.get(folderId);
            JSONArray debtArray = new JSONArray();
            if (debts != null) {
                debts.forEach(debt -> {
                    JSONObject d = new JSONObject();
                    try {
                        d.put("creditor", debt.getCreditor().getGmail());
                        d.put("debtor", debt.getDebtor().getGmail());
                        d.put("amount", debt.getAmount());
                        debtArray.put(d);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            assert metadata != null;
            metadata.put("debts", debtArray);

            JSONArray balancesArray = new JSONArray();
            if (balances != null) {
                balances.forEach((user, balance) -> {
                    JSONObject b = new JSONObject();
                    try {
                        b.put("gmail", user.getGmail());
                        b.put("amount", balance.getAmount());
                        b.put("inDebt", balance.isInDebt());
                        balancesArray.put(b);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            metadata.put("balances", balancesArray);

            JSONArray expensesArray = metadata.getJSONArray("expenses");
            JSONObject ex = new JSONObject();
            try {
                ex.put("name", expense.getName());
                ex.put("amount", expense.getAmount());
                expensesArray.put(ex);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            metadata.put("expenses", expensesArray);


            File metadataFile = findFileInFolder(folderId, "metadata.json");
            if (metadataFile != null) {
                uploadFileContent(metadataFile.getId(), metadata.toString());
            }
            File file = findFileInFolder(folderId, expense.getName() + ".expense.json");
            if (file != null) {
                String expenseFile = downloadFileContent(file.getId());
                assert expenseFile != null;
                JSONObject expenseJson = new JSONObject(expenseFile);
                addExpense(expense, folderId, expenseJson);
            }else{
                addExpense(expense, folderId, null);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //Deletes the debt from the group folder
    public void deleteExpense(String folderId, ArrayList<Debt> debts, Map<User, Balance> balances, Expense expense) {
        try {

            JSONObject metadata = metadatas.get(folderId);
            JSONArray debtArray = new JSONArray();
            if (debts != null) {
                debts.forEach(debt -> {
                    JSONObject d = new JSONObject();
                    try {
                        d.put("creditor", debt.getCreditor().getGmail());
                        d.put("debtor", debt.getDebtor().getGmail());
                        d.put("amount", debt.getAmount());
                        debtArray.put(d);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            assert metadata != null;
            metadata.put("debts", debtArray);

            JSONArray balancesArray = new JSONArray();
            if (balances != null) {
                balances.forEach((user, balance) -> {
                    JSONObject b = new JSONObject();
                    try {
                        b.put("gmail", user.getGmail());
                        b.put("amount", balance.getAmount());
                        b.put("inDebt", balance.isInDebt());
                        balancesArray.put(b);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            metadata.put("balances", balancesArray);
            // Check for the deleted expense in the metadata and deletes it
            if (metadata.has("expenses")) {
                JSONArray expensesJson = metadata.getJSONArray("expenses");
                JSONArray newExpensesArray = new JSONArray();
                for (int i = 0; i < expensesJson.length(); i++) {
                    JSONObject ex = expensesJson.getJSONObject(i);
                    if(!expense.getName().equals(ex.getString("name"))) {
                        newExpensesArray.put(ex);
                    }
                }
                metadata.put("expenses", newExpensesArray);
            }

            File metadataFile = findFileInFolder(folderId, "metadata.json");
            if (metadataFile != null) {
                uploadFileContent(metadataFile.getId(), metadata.toString());
            }
            //Delete the expense
            File file = findFileInFolder(folderId, expense.getName() + ".expense.json");
            assert file != null;
            File owner = driveService.files().get(folderId).setFields("owners").execute();
            String ownerEmail = owner.getOwners().get(0).getEmailAddress();
            if (ownerEmail.equals(user.getGmail())) {
                driveService.files().delete(file.getId()).execute();
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Adds a new expense to the group
    public void addExpense(Expense payment, String folderId, JSONObject expenseJson) {
        try {
            boolean newExpense = false;
            if (expenseJson == null) {
                newExpense = true;
                expenseJson = new JSONObject();
            }

            expenseJson.put("name", payment.getName());
            expenseJson.put("amount", payment.getAmount());

            JSONArray participants = new JSONArray();
            for (User u : payment.getParticipants()) {
                JSONObject p = new JSONObject();
                p.put("name", u.getName());
                p.put("gmail", u.getGmail());
                participants.put(p);
            }
            expenseJson.put("participants", participants);

            JSONObject creditors = new JSONObject();
            for (User u : payment.getCreditors().keySet()) {
                JSONObject b = new JSONObject();
                b.put("amount", payment.getCreditors().get(u));
                creditors.put(u.getGmail(), b);
            }
            expenseJson.put("creditors", creditors);

            JSONObject debtors = new JSONObject();
            for (User u : payment.getDebtors().keySet()) {
                JSONObject b = new JSONObject();
                b.put("amount", payment.getDebtors().get(u));
                debtors.put(u.getGmail(), b);
            }
            expenseJson.put("debtors", debtors);

            //If new expense, add it to the list of expenses
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(expenseJson.toString().getBytes(StandardCharsets.UTF_8));
            if (newExpense) {
                File fileMetadata = new File();
                fileMetadata.setName(payment.getName() + ".expense.json");
                fileMetadata.setParents(Collections.singletonList(folderId));

                com.google.api.client.http.AbstractInputStreamContent content =
                        new com.google.api.client.http.ByteArrayContent("application/json", outputStream.toByteArray());

                driveService.files().create(fileMetadata, content).setFields("id").execute();
            } else {
                File file = findFileInFolder(folderId, payment.getName() + ".expense.json");
                assert file != null;
                uploadFileContent(file.getId(), expenseJson.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Query the Google Drive API for a file with the given name in the given folder
    private File findFileInFolder(String folderId, String name) {
        try {

            FileList result = driveService.files().list()
                    .setQ(String.format(
                            "name = '%s' and '%s' in parents and trashed = false",
                            name.replace("'", "\\'"),
                            folderId
                    ))
                    .setFields("files(id, name)")
                    .setPageSize(1)
                    .execute();

            return result.getFiles().isEmpty() ? null : result.getFiles().get(0);
        } catch (Exception e) {
            return null;
        }
    }
    // Query the Google Drive API for all folders in the given folder
    private HashSet<String> listFoldersInFolder(String parentId) throws Exception {
        FileList result = driveService.files().list()
                .setQ("('" + parentId + "' in parents) and (mimeType = 'application/vnd.google-apps.folder' or (mimeType = 'application/vnd.google-apps.shortcut' and shortcutDetails.targetMimeType = 'application/vnd.google-apps.folder')) and trashed = false")
                .setFields("files(id, name, mimeType, shortcutDetails/targetId)")
                .execute();
        HashSet<String> folderIds = new HashSet<>();
        for (File groupFolder : result.getFiles()) {
            String folderId = groupFolder.getId();
            // Deal with shortcuts
            File folderMeta = driveService.files().get(folderId)
                    .setFields("id, mimeType, shortcutDetails/targetId")
                    .execute();

            if ("application/vnd.google-apps.shortcut".equals(folderMeta.getMimeType())) {
                folderId = folderMeta.getShortcutDetails().getTargetId(); // resolver target real
            }
            folderIds.add(folderId);

        }
        return folderIds;
    }

    // Query the Google Drive API for all files in the given folder
    private List<File> listFilesInFolder(String folderId) throws Exception {
        FileList result = driveService.files().list()
                .setQ("'" + folderId + "' in parents and trashed = false")
                .setFields("files(id, name)")
                .execute();
        return result.getFiles();
    }
    // Gets the user with the given gmail from the list of users
    private User findUserByGmail(ArrayList<User> users, String gmail) {
        for (User u : users) {
            if (u.getGmail().equals(gmail)) return u;
        }
        return null;
    }
    // Converts the JSONArray to an ArrayList of Users
    private ArrayList<User> loadUsersFromArray(JSONArray array) throws JSONException {
        ArrayList<User> users = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            User user = new User();
            user.setName(obj.getString("name"));
            user.setGmail(obj.getString("gmail"));
            users.add(user);
        }
        return users;
    }
    // Gets the content of the file with the given id
    private String downloadFileContent(String fileId) {
        try{
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return outputStream.toString(StandardCharsets.UTF_8);
            } else {
                return outputStream.toString();
            }
        }catch (Exception e) {
            return null;
        }
    }
    // Updates the content of the file with the given id
    private void uploadFileContent(String fileId, String content) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        driveService.files().update(fileId, null,
                        new com.google.api.client.http.ByteArrayContent("application/json", outputStream.toByteArray()))
                .execute();
    }
    // Creates a new folder with the given name in the given parent folder
    private String getOrCreateFolder() throws Exception {
        String query = "name = '" + "PaySplitter" + "' and mimeType = 'application/vnd.google-apps.folder' and trashed = false";
        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .execute();

        if (!result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }

        File fileMetadata = new File();
        fileMetadata.setName("PaySplitter");
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        File folder = driveService.files().create(fileMetadata)
                .setFields("id")
                .execute();

        return folder.getId();
    }
}
