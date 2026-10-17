<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
final class TgApi
{
    public static function dispatch(): void
    {
        header('Cache-Control: no-store, private');header('Pragma: no-cache');header('X-Content-Type-Options: nosniff');header('Referrer-Policy: no-referrer');
        try {
            TgConfig::requireHttps();if(!App::installed())throw new ApiError('not_installed','ابتدا Backend را نصب کنید.',503);Migrations::ensure();
            $method=$_SERVER['REQUEST_METHOD'] ?? 'GET';$route=(string)($_GET['r'] ?? '');
            if(!in_array($method,['GET','POST'],true))throw new ApiError('method','روش درخواست مجاز نیست.',405);
            // No CORS. Bearer isn't ambient; cross-site forms cannot supply it. Same-origin required for POST.
            if($method==='POST'){$origin=Http::header('Origin');$base=parse_url(App::baseUrl());$expected=$base['scheme'].'://'.$base['host'].(isset($base['port'])?':'.$base['port']:'');if($origin!=='' && !hash_equals($expected,$origin))throw new ApiError('origin','مبدأ درخواست مجاز نیست.',403);}
            $in=$method==='POST'?Http::body():$_GET;
            if($route==='auth') {if($method!=='POST')throw new ApiError('method','POST لازم است.',405);$data=TgAuth::exchange((string)($in['initData'] ?? ''));}
            else {$m=TgAuth::current();if($route==='export'){if($method!=='GET')throw new ApiError('method','GET لازم است.',405);self::export($m,$in);return;}$data=self::handle($m,$route,$method,$in);}
            Http::json(['ok'=>true,'data'=>$data]);
        }catch(ApiError $e){Http::json(['ok'=>false,'error'=>['code'=>$e->errorCode,'message'=>$e->getMessage()]],$e->status);}
        catch(Throwable $e){error_log('[divan] tg api failure '.get_class($e));TgEvents::signal('backend_error','tg_api',0,'tg_api:'.intdiv(Clock::now(),300));Http::json(['ok'=>false,'error'=>['code'=>'server','message'=>'خطای سرور؛ با شناسه رویداد در پنل بررسی کنید.']],500);}
    }
    public static function handle(array $m,string $route,string $method,array $in): array
    {
        if(in_array($route,['action','logout'],true)){
            if($method!=='POST')throw new ApiError('method','POST لازم است.',405);
            if($route==='logout'){Db::update('tg_sessions',['revoked_at'=>Clock::sql()],'id=?',[$m['session_id']]);return ['ok'=>true];}
            $action=(string)($in['action'] ?? '');$data=(array)($in['data'] ?? []);
            return self::mutate($m,$action,$data,(string)($in['request_key'] ?? ''),(string)($in['confirmation'] ?? ''),!empty($in['confirmed']));
        }
        if($method!=='GET')throw new ApiError('method','GET لازم است.',405);
        switch($route){
            case 'me':return ['me'=>TgAuth::publicMember($m)];
            case 'catalog':return TgRead::catalog($m);
            case 'dashboard':return TgRead::dashboard($m);
            case 'list':return TgRead::list($m,(string)($in['resource'] ?? ''),$in);
            case 'report':return self::report($m,$in);
            case 'detail':return TgRead::detail($m,(string)($in['kind'] ?? ''),(int)($in['id'] ?? 0));
            case 'gateways':return TgRead::gateways($m);
            case 'status':return TgRead::status($m);
            case 'alerts':return TgRead::alerts($m);
            case 'site':TgPolicy::require($m,'content.read');return ['config'=>SiteSettings::get()];
            case 'seo':TgPolicy::require($m,'content.read');return TgSeo::status();
            case 'search':
                $out=[];foreach(['users','transactions','orders','ads','support'] as $r){if(TgPolicy::can($m,TgRead::RESOURCES[$r][3]))$out[$r]=TgRead::list($m,$r,['q'=>$in['q'] ?? ''],5)['rows'];}return $out;
            default:throw new ApiError('not_found','مسیر موجود نیست.',404);
        }
    }
    public static function mutate(array $m,string $action,array $data,string $key,string $confirmation,bool $confirmed): array
    {
        TgPolicy::require($m,Management::permission($action));
        if(!preg_match('/^[a-zA-Z0-9_-]{16,64}$/D',$key))throw new ApiError('request_key','شناسه یک‌باراجرای درخواست لازم است.');
        if(!$confirmed)throw new ApiError('confirmation','درخواست باید از دکمه اجرای عملیات ارسال شود.',403);
        Management::reason($data);
        $hash=TgAuth::requestHash($action,$data);
        return Db::tx(function() use($m,$action,$data,$key,$confirmation,$hash) {
            // Serializes this session, preventing double execution across concurrent identical requests.
            $s=Db::one('SELECT id,expires_at,revoked_at,epoch FROM {tg_sessions} WHERE id=? FOR UPDATE',[$m['session_id']]);
            $live=TgPolicy::member((string)$m['telegram_id']);
            if(!$s || !$live || $s['revoked_at']!==null || Clock::ts($s['expires_at'])<=Clock::now() || (int)$s['epoch']!==(int)$live['epoch'])throw new ApiError('unauthorized','نشست نامعتبر است.',401);
            TgPolicy::require($live,Management::permission($action));
            $done=Db::one('SELECT request_hash,result_json FROM {tg_requests} WHERE session_id=? AND request_key=?',[$m['session_id'],$key]);
            if($done){if(!hash_equals($done['request_hash'],$hash))throw new ApiError('conflict','شناسه درخواست برای عملیات دیگری استفاده شده.',409);return json_decode($done['result_json'],true);}
            $result=Management::perform((int)$m['admin_id'],$action,$data,$live);
            Db::insert('tg_requests',['session_id'=>$m['session_id'],'request_key'=>$key,'request_hash'=>$hash,'result_json'=>json_encode($result,JSON_UNESCAPED_UNICODE),'created_at'=>Clock::sql()]);return $result;
        });
    }
    public static function report(array $m,array $in): array
    {
        TgPolicy::require($m,'reports');$resource=(string)($in['resource']??'');
        TgLimit::require('report:'.$m['id'],10,300);
        $in['page']=1;$result=TgRead::list($m,$resource,$in,500);
        if($result['total']>500)throw new ApiError('range','برای پیش‌نمایش و چاپ، بازه را به حداکثر ۵۰۰ ردیف محدود کنید؛ CSV تا ۱۰هزار ردیف را پشتیبانی می‌کند.');
        Audit::log('admin',(int)$m['admin_id'],'tg_report',$resource,null,'پیش‌نمایش خصوصی گزارش');
        return ['resource'=>$resource,'rows'=>$result['rows'],'total'=>$result['total'],'generated_at'=>Clock::sql()];
    }
    public static function export(array $m,array $in): void
    {
        TgPolicy::require($m,'reports');$r=(string)($in['resource'] ?? '');TgLimit::require('export:'.$m['id'],5,300);
        $in['page']=1;$result=TgRead::list($m,$r,$in,1000);if($result['total']>10000)throw new ApiError('range','بازه را کوچک‌تر کنید؛ سقف خروجی ۱۰هزار ردیف است.');
        Audit::log('admin',(int)$m['admin_id'],'tg_export',$r,null,'خروجی خصوصی CSV');
        header('Content-Type: text/csv; charset=utf-8');header('Content-Disposition: attachment; filename="divan-'.$r.'-'.gmdate('Ymd').'.csv"');
        $f=fopen('php://output','w');fwrite($f,"\xEF\xBB\xBF");$first=true;
        for($page=1;$page<=max(1,(int)ceil($result['total']/1000));$page++){
            $rows=$page===1?$result['rows']:TgRead::list($m,$r,array_merge($in,['page'=>$page]),1000)['rows'];
            foreach($rows as $row){if($first){fputcsv($f,array_keys($row));$first=false;}fputcsv($f,array_map(fn($v)=>is_string($v)&&preg_match('/^[\s\x00-\x1f]*[=+@-]/u',$v)?"'".$v:$v,array_values($row)));}
        }fclose($f);
    }
}
