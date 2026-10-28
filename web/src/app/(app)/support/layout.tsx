import { AuditNotice } from "@/components/shell/audit-notice";

export default function SupportLayout({ children }: LayoutProps<"/support">) {
  return (
    <>
      <AuditNotice />
      {children}
    </>
  );
}
