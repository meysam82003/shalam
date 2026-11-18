<?php
declare(strict_types=1);
if(!defined('DIVAN')){http_response_code(403);exit;}
final class AdminInbox
{
    public static function registration(int $uid): void {
        $u=Users::byId($uid);if($u===null)return;
        self::record('registration:'.$uid,'حساب تازه ساخته شد',$u['username'].' حساب کاربری ساخت.');
    }
    public static function purchase(int $id,bool $web=false): void {
        if($web){$p=Db::one("SELECT o.*,p.title AS plan_title FROM {pay_orders} o JOIN {plans} p ON p.id=o.plan_id WHERE o.id=? AND o.status='paid'",[$id]);}
        else{$p=Db::one("SELECT p.*,pl.title AS plan_title FROM {purchases} p LEFT JOIN {plans} pl ON pl.id=p.plan_id WHERE p.id=? AND p.status='verified'",[$id]);}
        if($p===null)return;$u=Users::byId((int)$p['user_id']);$name=$u['username']??'خریدار سایت (بدون حساب ثبت‌شده)';
        self::record(($web?'web:':'purchase:').$id,'خرید موفق اشتراک',$name.' — '.($p['plan_title']??$p['sku']??'اشتراک').' — '.(string)($p['amount_toman']??'').' تومان');
    }
    private static function record(string $source,string $title,string $body):void {
        Db::run('INSERT IGNORE INTO {admin_inbox} (source_key,title,body,created_at) VALUES (?,?,?,?)',[$source,mb_substr($title,0,120),mb_substr($body,0,500),Clock::sql()]);
    }
    public static function list(array $admin,int $after=0):array {
        if(!Admin::can('users')||!Admin::can('transactions'))throw new ApiError('admin_forbidden','دسترسی رویدادها مجاز نیست.',403);
        $read=(int)(Db::value('SELECT last_id FROM {admin_inbox_reads} WHERE admin_id=?',[$admin['id']])??0);
        $latest=(int)(Db::value('SELECT MAX(id) FROM {admin_inbox}')??0);
        $items=$after>0?Db::all('SELECT * FROM {admin_inbox} WHERE id>? ORDER BY id LIMIT 100',[$after]):Db::all('SELECT * FROM {admin_inbox} ORDER BY id DESC LIMIT 100');
        foreach($items as &$v){$v['id']=(int)$v['id'];$v['read']=$v['id']<=$read;}unset($v);
        return ['items'=>$items,'latest_id'=>$latest,'read_through'=>$read,'delivery'=>'fcm_with_periodic_fallback'];
    }
    public static function read(array $admin,int $id):array {
        $latest=(int)(Db::value('SELECT MAX(id) FROM {admin_inbox}')??0);$id=max(0,min($latest,$id));
        Db::run('INSERT INTO {admin_inbox_reads} (admin_id,last_id) VALUES (?,?) ON DUPLICATE KEY UPDATE last_id=GREATEST(last_id,VALUES(last_id))',[$admin['id'],$id]);return ['read_through'=>$id];
    }
}
