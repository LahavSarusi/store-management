package com.store.server.model;

import java.util.Objects;

/**
 * Employee account and personal details. {@code passwordHash} never leaves the server
 * (see {@code Dtos.EmployeeView}).
 */
public class Employee {

    private String username;
    private String passwordHash;
    private int employeeNumber;
    private String nationalId;
    private String fullName;
    private String phone;
    private String bankAccount;
    private String branchId;
    private Role role;
    private long passwordChangedAt;

    public Employee() { }

    public Employee(String username, String passwordHash, int employeeNumber, String nationalId, String fullName,
                    String phone, String bankAccount, String branchId, Role role, long passwordChangedAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.employeeNumber = employeeNumber;
        this.nationalId = nationalId;
        this.fullName = fullName;
        this.phone = phone;
        this.bankAccount = bankAccount;
        this.branchId = branchId;
        this.role = role;
        this.passwordChangedAt = passwordChangedAt;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public int getEmployeeNumber() { return employeeNumber; }
    public String getNationalId() { return nationalId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getBankAccount() { return bankAccount; }
    public String getBranchId() { return branchId; }
    public Role getRole() { return role; }
    public long getPasswordChangedAt() { return passwordChangedAt; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    public void setBranchId(String branchId) { this.branchId = branchId; }
    public void setRole(Role role) { this.role = role; }
    public void setPasswordChangedAt(long passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Employee e && Objects.equals(username, e.username);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(username);
    }

    @Override
    public String toString() {
        return username + " #" + employeeNumber + " (" + role + "@" + branchId + ")";
    }
}
