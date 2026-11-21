<?php
/** Renders existing PHP panel forms as semantic JSON for native Android widgets, without a WebView. */
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
require_once __DIR__.'/admin/pages_sales.php';
require_once __DIR__.'/admin/pages_config.php';
require_once __DIR__.'/admin/iran_ads.php';
require_once __DIR__.'/admin/push_page.php';
final class NativePanelRedirect extends RuntimeException { public string $query; public function __construct(string $query){parent::__construct('redirect');$this->query=$query;} }
final class NativePanelFile extends RuntimeException { public array $payload; public function __construct(array $file){parent::__construct('file');$this->payload=$file;} }
final class NativePanel
{
    public const PAGES = [
    'dashboard' => [SalesPages::class, 'dashboard'],
    'users' => [SalesPages::class, 'users'],
    'user' => [SalesPages::class, 'user'],
    'subscriptions' => [SalesPages::class, 'subscriptions'],
    'transactions' => [SalesPages::class, 'transactions'],
    'transaction' => [SalesPages::class, 'transaction'],
    'support' => [SalesPages::class, 'support'],
    'ticket' => [SalesPages::class, 'ticket'],
    'gifts' => [SalesPages::class, 'gifts'],
    'promos' => [SalesPages::class, 'promos'],
    'reports' => [SalesPages::class, 'reports'],
    'plans' => [ConfigPages::class, 'plans'],
    'campaigns' => [ConfigPages::class, 'campaigns'],
    'features' => [ConfigPages::class, 'features'],
    'ads' => [ConfigPages::class, 'ads'],
    'ad' => [ConfigPages::class, 'ad'],
    'placements' => [ConfigPages::class, 'placements'],
    'media' => [ConfigPages::class, 'media'],
    'adnetwork' => [ConfigPages::class, 'adnetwork'],
    'iranads' => [IranAdsPage::class, 'show'],
    'notices' => [ConfigPages::class, 'notices'],
    'push' => [PushPage::class, 'show'],
    'site' => [ConfigPages::class, 'site'],
    'stores' => [ConfigPages::class, 'stores'],
    'bridge' => [ConfigPages::class, 'bridge'],
    'paymethods' => [ConfigPages::class, 'paymethods'],
    'backups' => [ConfigPages::class, 'backups'],
    'audit' => [ConfigPages::class, 'audit'],
    'admins' => [ConfigPages::class, 'admins'],
    'account' => [ConfigPages::class, 'account'],
];
    public static function menu(): array {
        AdminMobile::auth('dashboard');
        $labels=['push'=>'اعلان بدون باز کردن اپ','dashboard'=>'داشبورد','users'=>'کاربران','subscriptions'=>'اشتراک‌ها','transactions'=>'تراکنش‌ها','support'=>'پشتیبانی','gifts'=>'هدیهٔ کاربران','promos'=>'کد تخفیف و هدیه','reports'=>'گزارش‌ها','plans'=>'پلن‌ها و قیمت‌ها','campaigns'=>'کمپین‌ها','features'=>'قابلیت‌ها','ads'=>'تبلیغات مستقیم','placements'=>'جایگاه‌ها','media'=>'رسانه‌ها','adnetwork'=>'تنظیمات تبلیغات مستقیم','iranads'=>'تبلیغات ایرانی و سکه','notices'=>'اعلان‌ها','site'=>'مدیریت سایت','stores'=>'بازار و مایکت','bridge'=>'تنظیمات پرداخت قدیمی','paymethods'=>'روش‌های پرداخت','backups'=>'پشتیبان و بازیابی','audit'=>'گزارش امنیتی','admins'=>'مدیران','account'=>'حساب و ورود دومرحله‌ای'];
        $items=[];foreach($labels as $key=>$title)if(Admin::can($key)&&($key!=='bridge'||!App::directPayments()))$items[]=['key'=>$key,'title'=>$title];return ['items'=>$items];
    }
    public static function handle(array $in): array {
        if(!class_exists(DOMDocument::class))throw new ApiError('xml_extension','افزونهٔ DOM/XML باید روی هاست فعال باشد.',503);
        $query=(string)($in['query']??'p=dashboard');$verb=(string)($in['verb']??'GET');
        if(strlen($query)>16000||!in_array($verb,['GET','POST'],true))throw new ApiError('panel','درخواست نامعتبر است.');
        $form=$in['fields']??[];if(!is_array($form)||count($form)>2000)throw new ApiError('panel','تعداد فیلدها زیاد است.');
        $pairs=[];foreach($form as $pair){if(!is_array($pair)||!isset($pair['name'])||!is_scalar($pair['value']??''))throw new ApiError('panel','فیلد نامعتبر است.');$pairs[]=rawurlencode((string)$pair['name']).'='.rawurlencode((string)($pair['value']??''));}
        $post=[];parse_str(implode('&',$pairs),$post);
        $savedGet=$_GET;$savedPost=$_POST;$savedMethod=$_SERVER['REQUEST_METHOD']??'GET';$savedQuery=$_SERVER['QUERY_STRING']??'';$savedSession=$_SESSION??[];
        try {
            Admin::$native=true;$_SESSION=[];
            $accountResult=false;
            for($redirect=0;$redirect<8;$redirect++){
                $get=[];parse_str($query,$get);$page=$get['p']??'dashboard';
                if(!is_string($page)||!isset(self::PAGES[$page]))throw new ApiError('panel','صفحهٔ مدیریت پیدا نشد.',404);
                // Account changes can revoke the current session. Return the one-time result from
                // this authorized request before the app needs to authenticate again.
                if (!$accountResult || $page!=='account' || $verb!=='GET') AdminMobile::auth($page,$verb==='POST');
                $_GET=$get;$_POST=$post;$_SERVER['REQUEST_METHOD']=$verb;$_SERVER['QUERY_STRING']=$query;
                Admin::checkPost($page==='account');
                ob_start();
                try {
                    call_user_func(self::PAGES[$page]);$html=ob_get_clean();
                    $nodes=self::document($html);
                    $title=($nodes[0]['kind']??'')==='page_title'?array_shift($nodes)['text']:'';
                    return ['query'=>$query,'title'=>$title,'nodes'=>$nodes];
                }
                catch(NativePanelRedirect $e){ob_end_clean();if($e->query==='p=logout')return ['logout'=>true,'nodes'=>[]];$accountResult=$page==='account'&&$verb==='POST'&&$e->query==='p=account';$query=$e->query;$verb='GET';$post=[];continue;}
                catch(NativePanelFile $e){ob_end_clean();return ['query'=>$query,'file'=>$e->payload,'nodes'=>[]];}
                catch(Throwable $e){ob_end_clean();throw $e;}
            }
            throw new ApiError('panel','تعداد انتقال‌ها زیاد است.');
        } finally {Admin::$native=false;$_GET=$savedGet;$_POST=$savedPost;$_SERVER['REQUEST_METHOD']=$savedMethod;$_SERVER['QUERY_STRING']=$savedQuery;$_SESSION=$savedSession;}
    }
    public static function document(string $html): array {
        $doc=new DOMDocument('1.0','UTF-8');$previous=libxml_use_internal_errors(true);$doc->loadHTML('<?xml encoding="UTF-8">'.$html,LIBXML_NONET|LIBXML_NOERROR|LIBXML_NOWARNING);libxml_clear_errors();libxml_use_internal_errors($previous);
        $xp=new DOMXPath($doc);$main=$xp->query('//main')->item(0)??$doc->getElementsByTagName('body')->item(0);$out=[];
        if($main!==null)self::walk($main,$out,$xp);
        return $out;
    }
    private static function clean(string $s): string {return trim(preg_replace('/\s+/u',' ',$s));}
    private static function walk(DOMNode $node,array &$out,DOMXPath $xp): void {
        if($node instanceof DOMText){$s=self::clean($node->textContent);if($s!=='')$out[]=['kind'=>'text','text'=>$s];return;}
        if(!($node instanceof DOMElement))return;$tag=strtolower($node->tagName);
        if(in_array($tag,['script','style','nav','aside','svg','canvas'],true))return;
        if ($tag==='button' && strpos($node->getAttribute('class'),'menu-toggle')!==false) return;
        if($tag==='form'){$out[]=self::form($node,$xp);return;}
        if ($tag==='h1') {$out[]=['kind'=>'page_title','text'=>self::clean($node->textContent)];return;}
        if (preg_match('/(?:^|\s)alert(?:\s|$)/',$node->getAttribute('class')) && !$xp->query('.//a|.//form',$node)->length) {
            $out[]=['kind'=>'alert','text'=>self::clean($node->textContent)];return;
        }
        if($tag==='table'){self::table($node,$out,$xp);return;}
        if($tag==='a'){$href=$node->getAttribute('href');$label=self::clean($node->textContent);if($href!==''&&$label!=='')$out[]=['kind'=>'link','text'=>$label,'href'=>$href];return;}
        if($tag==='img'){$out[]=['kind'=>'image','src'=>$node->getAttribute('src'),'alt'=>$node->getAttribute('alt')];return;}
        if(in_array($tag,['h1','h2','h3','h4','p','pre','code'],true)){$s=self::clean($node->textContent);if($s!=='')$out[]=['kind'=>$tag[0]==='h'?'heading':'text','text'=>$s];foreach($xp->query('.//a',$node)as $link)self::walk($link,$out,$xp);return;}
        if($tag==='tr')$out[]=['kind'=>'divider'];
        foreach($node->childNodes as $child)self::walk($child,$out,$xp);
    }
    private static function table(DOMElement $table,array &$out,DOMXPath $xp): void {
        $headers=[];
        foreach ($xp->query('./thead/tr[1]/th',$table) as $th) $headers[]=self::clean($th->textContent);
        foreach ($xp->query('./tbody/tr|./tr|./tfoot/tr',$table) as $row) {
            $cells=[];$i=0;
            foreach ($xp->query('./td|./th',$row) as $td) {
                $nodes=[];
                foreach ($td->childNodes as $node) self::walk($node,$nodes,$xp);
                $text=[];$actions=[];
                foreach ($nodes as $node) {
                    if (in_array($node['kind'],['link','form','image'],true)) $actions[]=$node;
                    elseif (isset($node['text'])) $text[]=$node['text'];
                }
                $span=max(1,(int)$td->getAttribute('colspan'));
                $cells[]=['label'=>$span===1?($headers[$i]??''):'','text'=>implode(' · ',$text),'items'=>$actions];
                $i+=$span;
            }
            if($cells!==[]) $out[]=['kind'=>'record','cells'=>$cells];
        }
    }
    private static function form(DOMElement $form,DOMXPath $xp): array {
        $fields=[];$buttons=[];$caption=[];
        foreach($xp->query('.//h2|.//h3|.//h4|.//p',$form)as $n)$caption[]=self::clean($n->textContent);
        foreach($xp->query('.//input|.//select|.//textarea|.//button',$form) as $e){
            $tag=strtolower($e->tagName);$type=$e->getAttribute('type')?:($tag==='button'?'submit':$tag);$name=$e->getAttribute('name');
            if($e->hasAttribute('disabled')||$name==='csrf')continue;
            if($tag==='button'||in_array($type,['submit','button','reset'],true)){if($type==='submit')$buttons[]=['name'=>$name,'value'=>$e->getAttribute('value'),'text'=>self::clean($e->textContent)?:$e->getAttribute('value'),'confirm'=>$e->hasAttribute('data-confirm')||strpos($e->getAttribute('class'),'danger')!==false||strpos($e->textContent,'حذف')!==false];continue;}
            if($name==='')continue;
            $label='';if($e->parentNode instanceof DOMElement && $e->parentNode->tagName==='label')$label=self::clean($e->parentNode->textContent);
            if($label===''&&$e->hasAttribute('id')){foreach($xp->query('//label')as $l)if($l->getAttribute('for')===$e->getAttribute('id')){$label=self::clean($l->textContent);break;}}
            if($label===''){for($prev=$e->previousSibling;$prev!==null;$prev=$prev->previousSibling){if($prev instanceof DOMElement&&in_array($prev->tagName,['label','b','strong'],true)){$label=self::clean($prev->textContent);break;}if($prev instanceof DOMElement&&in_array($prev->tagName,['input','select','textarea'],true))break;}}
            $value=$tag==='textarea'?$e->textContent:$e->getAttribute('value');$options=[];
            if($tag==='select'){foreach($e->getElementsByTagName('option')as $o)$options[]=['value'=>$o->hasAttribute('value')?$o->getAttribute('value'):$o->textContent,'label'=>self::clean($o->textContent),'selected'=>$o->hasAttribute('selected')];$type=$e->hasAttribute('multiple')?'multi':'select';}
            $fields[]=['name'=>$name,'type'=>$type,'value'=>$value,'label'=>$label?:$name,'checked'=>$e->hasAttribute('checked'),'readonly'=>$e->hasAttribute('readonly'),'required'=>$e->hasAttribute('required'),'hide_if_checked'=>$e->getAttribute('data-hide-if-checked'),'options'=>$options,'accept'=>$e->getAttribute('accept'),'placeholder'=>$e->getAttribute('placeholder')];
        }
        return ['kind'=>'form','action'=>$form->getAttribute('action'),'method'=>strtoupper($form->getAttribute('method')?:'GET'),'caption'=>implode(' • ',$caption),'fields'=>$fields,'buttons'=>$buttons];
    }
}
