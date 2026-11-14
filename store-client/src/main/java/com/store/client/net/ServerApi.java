package com.store.client.net;

import com.google.gson.reflect.TypeToken;
import com.store.client.dto.Dtos.Branch;
import com.store.client.dto.Dtos.ChatLeaveRequest;
import com.store.client.dto.Dtos.ChatMessage;
import com.store.client.dto.Dtos.ChatRequestIdRequest;
import com.store.client.dto.Dtos.ChatRequestResult;
import com.store.client.dto.Dtos.ChatSendRequest;
import com.store.client.dto.Dtos.ChatSessionRef;
import com.store.client.dto.Dtos.ChatSessionRequest;
import com.store.client.dto.Dtos.ChatSummary;
import com.store.client.dto.Dtos.ChatTargetRequest;
import com.store.client.dto.Dtos.Customer;
import com.store.client.dto.Dtos.CustomerRequest;
import com.store.client.dto.Dtos.EmployeeRequest;
import com.store.client.dto.Dtos.EmployeeView;
import com.store.client.dto.Dtos.InventoryItem;
import com.store.client.dto.Dtos.InventoryView;
import com.store.client.dto.Dtos.LogEntry;
import com.store.client.dto.Dtos.LogQueryRequest;
import com.store.client.dto.Dtos.LoginRequest;
import com.store.client.dto.Dtos.LoginResult;
import com.store.client.dto.Dtos.PasswordChangeRequest;
import com.store.client.dto.Dtos.PasswordPolicy;
import com.store.client.dto.Dtos.ReportExport;
import com.store.client.dto.Dtos.ReportRequest;
import com.store.client.dto.Dtos.ReportResult;
import com.store.client.dto.Dtos.RestockRequest;
import com.store.client.dto.Dtos.SaleQuote;
import com.store.client.dto.Dtos.SaleRequest;
import com.store.client.dto.Dtos.SaleResult;
import com.store.client.dto.Dtos.TranscriptView;
import com.store.client.dto.Dtos.UsernameRequest;
import com.store.client.dto.RequestType;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Typed calls for every server route. Each method returns immediately; the future completes on the reader thread. */
public final class ServerApi {

    private static final Type BRANCHES = new TypeToken<List<Branch>>() { }.getType();
    private static final Type EMPLOYEES = new TypeToken<List<EmployeeView>>() { }.getType();
    private static final Type CUSTOMERS = new TypeToken<List<Customer>>() { }.getType();
    private static final Type CHATS = new TypeToken<List<ChatSummary>>() { }.getType();
    private static final Type LOG_ENTRIES = new TypeToken<List<LogEntry>>() { }.getType();
    private static final Type STRINGS = new TypeToken<List<String>>() { }.getType();

    private final Connection connection;

    public ServerApi(Connection connection) {
        this.connection = connection;
    }

    public Connection connection() {
        return connection;
    }

    // ---------------------------------------------------------------- auth
    public CompletableFuture<LoginResult> login(String username, String password) {
        return call(RequestType.LOGIN, new LoginRequest(username, password), LoginResult.class);
    }

    public CompletableFuture<Void> logout() {
        return call(RequestType.LOGOUT, null, null);
    }

    public CompletableFuture<Void> changePassword(String currentPassword, String newPassword) {
        return call(RequestType.PASSWORD_CHANGE, new PasswordChangeRequest(currentPassword, newPassword), null);
    }

    public CompletableFuture<List<Branch>> branches() {
        return call(RequestType.BRANCH_LIST, null, BRANCHES);
    }

    // ---------------------------------------------------------------- admin
    public CompletableFuture<List<EmployeeView>> employees() {
        return call(RequestType.EMPLOYEE_LIST, null, EMPLOYEES);
    }

    public CompletableFuture<EmployeeView> createEmployee(EmployeeRequest request) {
        return call(RequestType.EMPLOYEE_CREATE, request, EmployeeView.class);
    }

    public CompletableFuture<EmployeeView> updateEmployee(EmployeeRequest request) {
        return call(RequestType.EMPLOYEE_UPDATE, request, EmployeeView.class);
    }

    public CompletableFuture<Void> deleteEmployee(String username) {
        return call(RequestType.EMPLOYEE_DELETE, new UsernameRequest(username), null);
    }

    public CompletableFuture<PasswordPolicy> passwordPolicy() {
        return call(RequestType.PASSWORD_POLICY_GET, null, PasswordPolicy.class);
    }

    public CompletableFuture<PasswordPolicy> setPasswordPolicy(PasswordPolicy policy) {
        return call(RequestType.PASSWORD_POLICY_SET, policy, PasswordPolicy.class);
    }

    // ---------------------------------------------------------------- customers
    public CompletableFuture<List<Customer>> customers() {
        return call(RequestType.CUSTOMER_LIST, null, CUSTOMERS);
    }

    public CompletableFuture<Customer> createCustomer(CustomerRequest request) {
        return call(RequestType.CUSTOMER_CREATE, request, Customer.class);
    }

    public CompletableFuture<Customer> updateCustomer(CustomerRequest request) {
        return call(RequestType.CUSTOMER_UPDATE, request, Customer.class);
    }

    // ---------------------------------------------------------------- inventory and sales
    public CompletableFuture<InventoryView> inventory() {
        return call(RequestType.INVENTORY_LIST, null, InventoryView.class);
    }

    public CompletableFuture<InventoryItem> restock(RestockRequest request) {
        return call(RequestType.INVENTORY_RESTOCK, request, InventoryItem.class);
    }

    public CompletableFuture<SaleQuote> quote(SaleRequest request) {
        return call(RequestType.SALE_QUOTE, request, SaleQuote.class);
    }

    public CompletableFuture<SaleResult> sell(SaleRequest request) {
        return call(RequestType.SALE_CREATE, request, SaleResult.class);
    }

    // ---------------------------------------------------------------- reports
    public CompletableFuture<ReportResult> report(ReportRequest request) {
        return call(RequestType.REPORT_SALES, request, ReportResult.class);
    }

    public CompletableFuture<ReportExport> exportReport(ReportRequest request) {
        return call(RequestType.REPORT_EXPORT, request, ReportExport.class);
    }

    // ---------------------------------------------------------------- chat
    public CompletableFuture<ChatRequestResult> requestChat(String targetBranchId) {
        return call(RequestType.CHAT_REQUEST, new ChatTargetRequest(targetBranchId), ChatRequestResult.class);
    }

    public CompletableFuture<ChatSessionRef> acceptChat(String requestId) {
        return call(RequestType.CHAT_ACCEPT, new ChatRequestIdRequest(requestId), ChatSessionRef.class);
    }

    public CompletableFuture<Void> declineChat(String requestId) {
        return call(RequestType.CHAT_DECLINE, new ChatRequestIdRequest(requestId), null);
    }

    public CompletableFuture<ChatMessage> sendChat(String sessionId, String text) {
        return call(RequestType.CHAT_SEND, new ChatSendRequest(sessionId, text), ChatMessage.class);
    }

    public CompletableFuture<Void> joinChat(String sessionId) {
        return call(RequestType.CHAT_JOIN, new ChatSessionRequest(sessionId), null);
    }

    public CompletableFuture<Void> leaveChat(String sessionId, boolean saveTranscript) {
        return call(RequestType.CHAT_LEAVE, new ChatLeaveRequest(sessionId, saveTranscript), null);
    }

    public CompletableFuture<List<ChatSummary>> activeChats() {
        return call(RequestType.CHAT_LIST_ACTIVE, null, CHATS);
    }

    // ---------------------------------------------------------------- logs
    public CompletableFuture<List<LogEntry>> logs(LogQueryRequest query) {
        return call(RequestType.LOG_LIST, query, LOG_ENTRIES);
    }

    public CompletableFuture<List<String>> transcripts() {
        return call(RequestType.CHAT_TRANSCRIPT_LIST, null, STRINGS);
    }

    public CompletableFuture<TranscriptView> transcript(String sessionId) {
        return call(RequestType.CHAT_TRANSCRIPT_GET, new ChatSessionRequest(sessionId), TranscriptView.class);
    }

    // ---------------------------------------------------------------- internals

    /** {@code resultType == null} means the route answers with no payload. */
    private <T> CompletableFuture<T> call(RequestType type, Object payload, Type resultType) {
        return connection.send(type, payload)
                .thenApply(json -> resultType == null ? null : JsonCodec.decode(json, resultType));
    }
}
