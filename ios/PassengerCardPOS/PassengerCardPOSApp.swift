import SwiftUI
import VisionKit

@main
struct PassengerCardPOSApp: App {
    var body: some Scene {
        WindowGroup {
            TabView {
                CustomerCheckinView()
                    .tabItem { Label("Hành khách", systemImage: "person") }
                POSView()
                    .tabItem { Label("POS", systemImage: "creditcard") }
            }
        }
    }
}

struct POSView: View {
    @State private var server = ""
    @State private var username = ""
    @State private var password = ""
    @State private var terminal = ""
    @State private var cruise = ""
    @State private var deviceCredential = ""
    @State private var usesDeviceToken = false
    @State private var credentials: POSCredentials?
    @State private var code = ""
    @State private var preview: PassengerCard?
    @State private var cardIdentifier: String?
    @State private var result: PassengerCard?
    @State private var errorMessage: String?
    @State private var busy = false
    @State private var showingScanner = false
    @State private var reader = CardReader()

    var body: some View {
        NavigationStack {
            Form {
                if let credentials {
                    operationSections(api: POSAPI(credentials: credentials))
                    Button("Đăng xuất", role: .destructive) {
                        run {
                            try await POSAPI(credentials: credentials).signOut()
                            self.credentials = nil
                            code = ""
                            resetClaim()
                        }
                    }
                    .disabled(busy)
                } else {
                    loginSection
                }
                if let errorMessage {
                    Text(errorMessage).foregroundStyle(.red)
                }
                if busy { ProgressView() }
            }
            .navigationTitle("POS cấp thẻ")
            .sheet(isPresented: $showingScanner) {
                ClaimScanner { value in
                    showingScanner = false
                    code = value
                }
                .ignoresSafeArea()
            }
        }
    }

    private var loginSection: some View {
        Section("Đăng nhập POS") {
            TextField("URL HTTPS của API", text: $server)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
            TextField("Tên đăng nhập nhân viên", text: $username)
                .textInputAutocapitalization(.never)
            SecureField("Mật khẩu nhân viên", text: $password)
            TextField("ID thiết bị POS", text: $terminal)
                .textInputAutocapitalization(.never)
            TextField("ID chuyến", text: $cruise)
                .textInputAutocapitalization(.never)
            Toggle("Dùng token của thiết bị POS", isOn: $usesDeviceToken)
            SecureField(usesDeviceToken ? "Token thiết bị" : "Mã bí mật thiết bị", text: $deviceCredential)

            Button("Mở phiên POS", action: signIn)
                .disabled(busy)
        }
    }

    private func operationSections(api: POSAPI) -> some View {
        Group {
            Section("Cấp thẻ") {
                if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                    Button("Quét mã trên app khách") { showingScanner = true }
                        .disabled(busy)
                }
                TextField("Mã nhận thẻ", text: $code)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .onChange(of: code) { _ in
                        preview = nil
                        cardIdentifier = nil
                    }
                    .disabled(busy)
                Button("Tra cứu khách") {
                    let claim = code.trimmingCharacters(in: .whitespacesAndNewlines)
                    run {
                        resetClaim()
                        preview = try await api.preview(code: claim)
                    }
                }
                .disabled(busy || code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                if let preview {
                    LabeledContent("Hành khách", value: preview.passengerName)
                    Button("Đọc thẻ NFC") {
                        readCard { cardIdentifier = $0 }
                    }
                    .disabled(busy)
                }
                if let cardIdentifier {
                    LabeledContent("Mã thẻ đã đọc", value: cardIdentifier)
                    Button("Xác nhận cấp thẻ") {
                        let claim = code.trimmingCharacters(in: .whitespacesAndNewlines)
                        run {
                            let assigned = try await api.confirm(code: claim, cardIdentifier: cardIdentifier)
                            code = ""
                            resetClaim()
                            result = assigned
                        }
                    }
                    .disabled(busy)
                }
            }
            Section("Tra cứu thẻ đã cấp") {
                Button("Chạm thẻ để tra cứu") {
                    readCard { identifier in
                        run { result = try await api.lookup(cardIdentifier: identifier) }
                    }
                }
                .disabled(busy)
                if let result {
                    LabeledContent("Hành khách", value: result.passengerName)
                    if let confirmedAt = result.confirmedAt {
                        LabeledContent("Thời điểm cấp", value: confirmedAt)
                    }
                }
            }
        }
    }

    private func signIn() {
        guard let url = POSAPI.secureBaseURL(server),
              let terminalID = UUID(uuidString: terminal.trimmingCharacters(in: .whitespacesAndNewlines)),
              let cruiseID = UUID(uuidString: cruise.trimmingCharacters(in: .whitespacesAndNewlines)),
              !username.isEmpty, !password.isEmpty, !deviceCredential.isEmpty else {
            errorMessage = "Nhập URL HTTPS, ID thiết bị, ID chuyến và thông tin đăng nhập."
            return
        }
        run {
            credentials = try await POSAPI.signIn(
                baseURL: url, username: username, password: password, terminalID: terminalID,
                cruiseID: cruiseID, deviceCredential: deviceCredential, usesDeviceToken: usesDeviceToken)
            password = ""
            deviceCredential = ""
        }
    }

    private func readCard(_ onRead: @escaping (String) -> Void) {
        errorMessage = nil
        busy = true
        reader.scan { output in
            busy = false
            switch output {
            case let .success(identifier): onRead(identifier)
            case let .failure(error): errorMessage = error.localizedDescription
            }
        }
    }

    private func resetClaim() {
        preview = nil
        cardIdentifier = nil
        result = nil
        errorMessage = nil
    }

    private func run(_ operation: @escaping () async throws -> Void) {
        guard !busy else { return }
        busy = true
        errorMessage = nil
        Task { @MainActor in
            defer { busy = false }
            do { try await operation() }
            catch { errorMessage = error.localizedDescription }
        }
    }
}
