import SwiftUI

struct BreachMonitorView: View {
    @State private var monitorEmail = ""

    let monitored = ["admin@kaso.io", "operations@kaso.io"]

    let breaches: [Breach] = [
        Breach(title: "Collection #1 Dump", breachDate: "Jan 17, 2019", discoveredDate: "Jan 18, 2019", compromisedFields: ["Passwords", "Emails"], status: .unresolved),
        Breach(title: "LinkedIn Data Scraping", breachDate: "Jun 22, 2021", discoveredDate: "Jun 29, 2021", compromisedFields: ["Names", "Job Titles"], status: .resolved)
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    HStack(spacing: 12) {
                        Image(systemName: "shield.lefthalf.filled")
                            .font(.title2)
                            .foregroundColor(Theme.mint)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Breach Monitor")
                                .font(.title2.bold())
                                .foregroundColor(Theme.textPrimary)
                            Text("k-Anonymity privacy-preserving leak detection.")
                                .font(.caption)
                                .foregroundColor(Theme.textSecondary)
                        }
                    }

                    CardContainer {
                        VStack(alignment: .leading, spacing: 12) {
                            SectionLabel(text: "Add Target to Monitor")
                            HStack {
                                Image(systemName: "at")
                                    .foregroundColor(Theme.textSecondary)
                                TextField("Enter email address...", text: $monitorEmail)
                                    .foregroundColor(Theme.textPrimary)
                            }
                            .padding()
                            .background(Theme.cardBackgroundLight)
                            .cornerRadius(Theme.cornerRadius)

                            PrimaryButton(title: "Check Leaks", icon: "magnifyingglass") {}

                            VStack(alignment: .leading, spacing: 8) {
                                Text("Monitored:")
                                    .font(.caption2)
                                    .foregroundColor(Theme.textSecondary)
                                ForEach(monitored, id: \.self) { email in
                                    HStack {
                                        Text(email)
                                            .font(.caption)
                                            .foregroundColor(Theme.textPrimary)
                                        Image(systemName: email == monitored.first ? "checkmark.seal.fill" : "exclamationmark.triangle.fill")
                                            .font(.caption2)
                                            .foregroundColor(email == monitored.first ? Theme.mint : Theme.gold)
                                    }
                                }
                            }
                        }
                    }

                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "exclamationmark.circle.fill")
                            .foregroundColor(Theme.danger)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Action Required")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(Theme.textPrimary)
                            Text("1 active breach detected for monitored addresses. Immediate password rotation recommended.")
                                .font(.caption)
                                .foregroundColor(Theme.textSecondary)
                        }
                    }
                    .padding()
                    .background(Theme.dangerBackground)
                    .cornerRadius(Theme.cornerRadius)

                    SectionLabel(text: "Leak History")
                    VStack(spacing: 12) {
                        ForEach(breaches) { breach in
                            BreachCard(breach: breach)
                        }
                    }
                }
                .padding(20)
            }
            .background(Theme.background.ignoresSafeArea())
        }
    }
}

struct BreachCard: View {
    let breach: Breach

    var body: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(breach.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(Theme.textPrimary)
                    Spacer()
                    PillBadge(
                        text: breach.status.rawValue,
                        color: breach.status == .unresolved ? Theme.danger : Theme.mint
                    )
                }
                Text("Breach Date: \(breach.breachDate) • Discovered: \(breach.discoveredDate)")
                    .font(.caption2)
                    .foregroundColor(Theme.textSecondary)

                VStack(alignment: .leading, spacing: 6) {
                    Text("Compromised:")
                        .font(.caption2)
                        .foregroundColor(Theme.textSecondary)
                    HStack {
                        ForEach(breach.compromisedFields, id: \.self) { field in
                            HStack(spacing: 4) {
                                Image(systemName: "tag.fill").font(.caption2)
                                Text(field).font(.caption2)
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Theme.cardBackgroundLight)
                            .foregroundColor(Theme.textSecondary)
                            .cornerRadius(6)
                        }
                    }
                }

                if breach.status == .unresolved {
                    PrimaryButton(title: "Rotate Password", icon: "arrow.triangle.2.circlepath", color: Theme.danger, textColor: .white) {}
                } else {
                    HStack {
                        Image(systemName: "checkmark")
                        Text("Action Taken")
                    }
                    .font(.caption.weight(.medium))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Theme.cardBackgroundLight)
                    .foregroundColor(Theme.textSecondary)
                    .cornerRadius(Theme.cornerRadius)
                }
            }
        }
    }
}

#Preview {
    BreachMonitorView()
}
