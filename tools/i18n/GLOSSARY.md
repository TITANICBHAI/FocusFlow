# Translation glossary

Keep the same word for the same thing across the whole app. These match the translated website.

| English | de | es | fr | ja | ko | zh |
|---|---|---|---|---|---|---|
| Focus (session/tab) | Fokus | Enfoque | Focus | 集中 | 집중 | 专注 |
| Schedule (tab) | Zeitplan | Horario | Planning | スケジュール | 일정 | 日程 |
| Defense (tab) | Schutz | Defensa | Défense | 防御 | 방어 | 防御 |
| Stats | Statistiken | Estadísticas | Statistiques | 統計 | 통계 | 统计 |
| Settings | Einstellungen | Ajustes | Réglages | 設定 | 설정 | 设置 |
| block / blocking | blockieren | bloquear / bloqueo | bloquer / blocage | ブロック | 차단 | 屏蔽 |
| permission | Berechtigung | permiso | autorisation | 権限 | 권한 | 权限 |
| Accessibility Service | Bedienungshilfen-Dienst | Servicio de accesibilidad | Service d’accessibilité | ユーザー補助サービス | 접근성 서비스 | 无障碍服务 |
| Usage Access | Nutzungszugriff | Acceso de uso | Accès à l’utilisation | 使用状況へのアクセス | 사용 기록 접근 | 使用情况访问权限 |
| Notifications | Benachrichtigungen | Notificaciones | Notifications | 通知 | 알림 | 通知 |

The product name FocusFlow is never translated. Use Android placeholders exactly as in English
(%1$s, %1$d) and keep them. Write a literal % as %%. Keep the tone short and plain.

Brand-style feature names stay in English everywhere (for example "Nuclear Mode"); words that are only
labels (Focus Mode, Pomodoro Mode, Allowed apps) are translated.

Text that is data, not words: `*date_pattern*` keys are java.time patterns. Translators adapt the order
and quote any literal text in single quotes (for example `M'月'd'日'`); the unit test builds every pattern.
