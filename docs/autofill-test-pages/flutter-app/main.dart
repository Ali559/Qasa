import 'package:flutter/material.dart';

// Autofill test app (Qasa Phase 12): fields are NOT in an AutofillGroup and have no
// autofillHints, only placeholders, like the Dozarawa app. Android then reports only the
// focused field to the autofill service.
void main() => runApp(const MaterialApp(home: Home()));

class Home extends StatelessWidget {
  const Home({super.key});
  @override
  Widget build(BuildContext context) => DefaultTabController(
        length: 2,
        child: Scaffold(
          appBar: AppBar(title: const Text('Flutter AF test'), bottom: const TabBar(tabs: [Tab(text: 'Sign up'), Tab(text: 'Sign in')])),
          body: const TabBarView(children: [SignUp(), SignIn()]),
        ),
      );
}

Widget field(String hint, {bool obscure = false}) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: TextField(obscureText: obscure, decoration: InputDecoration(hintText: hint, border: const OutlineInputBorder())),
    );

class SignUp extends StatelessWidget {
  const SignUp({super.key});
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(20), children: [
        const Text('Join our community', style: TextStyle(fontSize: 24)),
        field('Full name'), field('Username'), field('Email'), field('Password', obscure: true),
        FilledButton(onPressed: () {}, child: const Text('Continue')),
      ]);
}

class SignIn extends StatelessWidget {
  const SignIn({super.key});
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(20), children: [
        field('Username'), field('Password', obscure: true),
        FilledButton(onPressed: () {}, child: const Text('Log in')),
      ]);
}
