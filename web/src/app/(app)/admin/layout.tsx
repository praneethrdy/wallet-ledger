import { AuditNotice } from "@/components/shell/audit-notice";

export default function AdminLayout({ children }: LayoutProps<"/admin">) {
  return (
    <>
      <AuditNotice />
      {children}
    </>
  );
}
