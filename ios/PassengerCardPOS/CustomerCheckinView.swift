import CoreImage
import CoreImage.CIFilterBuiltins
import SwiftUI

struct CustomerCheckinView: View {
    @State private var server = ""
    @State private var username = ""
    @State private var password = ""
    @State private var account: Account?
    @State private var passenger = ""
    @State private var contactName = ""
    @State private var contactPhone = ""
    @State private var status: String?
    @State private var claim: POSAPI.CardClaim?
    @State private var busy = false
    @State private var errorMessage: String?

    private struct Account {
        let baseURL: URL
        let token: String
    }

    var body: some View {
        NavigationStack {
            Form {
                if let account {
                    checkinSection(account)
                    if let claim, let image = qrCode(for: claim.code) {
                        Section("Mã nhận thẻ") {
                            Image(decorative: image, scale: 1)
                                .interpolation(.none)
                                .resizable()
                                .scaledToFit()
                                .frame(maxWidth: 224)
                                .padding(12)
                                .background(.white)
                                .accessibilityLabel("Mã QR nhận thẻ")
                            Text(claim.code).font(.caption.monospaced()).textSelection(.enabled)
                            if let expiry = ISO8601DateFormatter().date(from: claim.expiresAt)
                                ?? fractionalDate(claim.expiresAt) {
                                LabeledContent("Hết hạn", value: expiry.formatted(date: .omitted, time: .shortened))
                            }
                        }
                    }
                    Button("Đăng xuất", role: .destructive) {
                        self.account = nil
                        passenger = ""
                        contactName = ""
                        contactPhone = ""
                        status = nil
                        claim = nil
                        errorMessage = nil
                    }
                    .disabled(busy)
                } else {
                    Section("Đăng nhập hành khách") {
                        TextField("URL HTTPS của API", text: $server).keyboardType(.URL)
                        TextField("Tên đăng nhập", text: $username).textInputAutocapitalization(.never)
                        SecureField("Mật khẩu", text: $password)
                        Button("Đăng nhập") { signIn() }.disabled(busy)
                    }
                }
                if let errorMessage {
                    Text(errorMessage).foregroundStyle(.red)
                }
                if busy { ProgressView() }
            }
            .navigationTitle("Check-in")
        }
    }

    private func checkinSection(_ account: Account) -> some View {
        Section("Check-in online") {
            TextField("ID hành khách trong booking", text: $passenger)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .onChange(of: passenger) { _ in
                    claim = nil
                    status = nil
                }
                .disabled(busy)
            TextField("Tên người liên hệ khẩn cấp", text: $contactName)
            TextField("Số điện thoại khẩn cấp", text: $contactPhone).keyboardType(.phonePad)
            Button("Bắt đầu check-in") {
                withPassenger { id in
                    status = try await POSAPI.startCustomerCheckin(
                        baseURL: account.baseURL, token: account.token, passengerID: id,
                        contactName: contactName, contactPhone: contactPhone).checkinStatus
                }
            }
            .disabled(busy)
            Button("Hoàn tất check-in") {
                withPassenger { id in
                    status = try await POSAPI.completeCustomerCheckin(
                        baseURL: account.baseURL, token: account.token, passengerID: id).checkinStatus
                }
            }
            .disabled(busy)
            Button("Lấy mã nhận thẻ") {
                withPassenger { id in
                    claim = try await POSAPI.customerClaim(
                        baseURL: account.baseURL, token: account.token, passengerID: id)
                }
            }
            .disabled(busy)
            if let status {
                LabeledContent("Trạng thái", value: status == "BoardingReady" ? "Đã hoàn tất" : "Đang thực hiện")
            }
        }
    }

    private func signIn() {
        guard let url = POSAPI.secureBaseURL(server), !username.isEmpty, !password.isEmpty else {
            errorMessage = "Nhập URL HTTPS và thông tin đăng nhập."
            return
        }
        run {
            let token = try await POSAPI.signInAccount(baseURL: url, username: username, password: password)
            account = Account(baseURL: url, token: token)
            password = ""
        }
    }

    private func withPassenger(_ operation: @escaping (UUID) async throws -> Void) {
        guard let id = UUID(uuidString: passenger.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            errorMessage = "Nhập ID hành khách trong booking."
            return
        }
        run { try await operation(id) }
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

    private func fractionalDate(_ value: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: value)
    }

    private func qrCode(for value: String) -> CGImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(value.utf8)
        guard let output = filter.outputImage else { return nil }
        return CIContext().createCGImage(output, from: output.extent)
    }
}
