//
//  qasaApp.swift
//  qasa
//
//  Created by MacbookShop on 8/2/26.
//

import SwiftUI
import SwiftData

@main
struct qasaApp: App {
    
    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
        }
    }
    struct RootView: View {
        @State private var vaultCreated = false
        
        var body: some View {
            if vaultCreated {
                MainTabView()
            } else {
                CreateVaultView(onVaultCreated: { vaultCreated = true })
            }
        }
    }
}
