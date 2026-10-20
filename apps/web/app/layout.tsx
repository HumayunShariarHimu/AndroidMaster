import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = { title: "AndroidMaster — Personal Device Hub", description: "Private Android device control workspace", applicationName: "AndroidMaster" };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) { return <html lang="en"><body>{children}</body></html>; }
