# Stella Android Groq (protótipo 0.1.0)

Aplicativo Android experimental com chat Groq, histórico armazenado localmente e interface escura em português.

## Status
**Protótipo independente, não é o aplicativo oficial Stella.** Não implementa ainda agenda, notificações, voz, integração Minha Sala, execução de ferramentas ou paridade funcional com Stella. Não requer Cloudflare. Requer internet e chave Groq própria, sujeita aos limites da conta.

## APK
Abra **Actions > Android APK**, execute o workflow ou aguarde o push, depois baixe o artefato de debug. O APK de debug não possui assinatura estável garantida entre builds; para atualizações instaláveis por cima é necessária assinatura de release persistente.

## Segurança
A chave fica em SharedPreferences privadas do aplicativo (não criptografadas em repouso). Não compartilhe backups ou aparelhos desbloqueados. A conversa enviada para a Groq é processada externamente. O histórico fica em SharedPreferences locais e backup Android está desativado.

## Origem e créditos
Projeto independente inspirado no conceito de assistente pessoal da [Stella](https://github.com/1missthesun/stella), de 1missthesun (licença MIT). Nenhum código da Stella original foi copiado nesta versão. Antes de reutilizar código original, preservar avisos de licença e atribuição.
