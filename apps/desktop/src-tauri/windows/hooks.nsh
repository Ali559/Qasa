; Qasa's NSIS installer hooks (tauri.conf.json > bundle > windows > nsis > installerHooks).
; Saved as UTF-8 with a BOM so makensis reads the Arabic text as UTF-8.
;
; Before uninstalling, warn that the vault lives only on this computer and suggest a
; backup, with Cancel to keep Qasa. The texts are the app's backup reminder
; (l10n/strings.json: backup.reminder.title, backup.reminder.body), copied by hand.
;
; Never shown when nobody is there to answer, or when the uninstall is part of an update:
; - silent runs (/S): IfSilent;
; - passive runs (/P): $PassiveMode, set by Tauri's un.onInit;
; - updates (/UPDATE): $UpdateMode, also set in un.onInit. The Tauri updater starts the
;   installer with /UPDATE (plus /P or /S), and the installer passes /UPDATE and /P on to
;   the old uninstaller, so an update never reaches the message box.
; This macro runs at the top of Tauri's "Section Uninstall", after un.onInit and before
; anything is removed. Cancel quits with exit code 1, which Tauri's installer reads as
; "the user cancelled the uninstaller" when a reinstall started it.

!macro NSIS_HOOK_PREUNINSTALL
  IfSilent qasa_backup_warning_done
  ${If} $PassiveMode <> 1
  ${AndIf} $UpdateMode <> 1
    ${If} $LANGUAGE = 1025 ; Arabic
      MessageBox MB_OKCANCEL|MB_ICONEXCLAMATION|MB_DEFBUTTON2|MB_RIGHT|MB_RTLREADING "انسخ خزنتك احتياطيًا$\n$\nخزنتك موجودة على هذا الحاسوب فقط. إذا فقدته أو حذفت قاصة، فالنسخة الاحتياطية هي الطريقة الوحيدة لاستعادة كلمات مرورك." /SD IDOK IDOK qasa_backup_warning_done
    ${Else}
      MessageBox MB_OKCANCEL|MB_ICONEXCLAMATION|MB_DEFBUTTON2 "Back up your vault$\n$\nYour vault is only on this computer. If you lose it or delete Qasa, a backup is the only way to get your passwords back." /SD IDOK IDOK qasa_backup_warning_done
    ${EndIf}
    ; Cancel: keep Qasa installed.
    SetErrorLevel 1
    Quit
  ${EndIf}
  qasa_backup_warning_done:
!macroend
