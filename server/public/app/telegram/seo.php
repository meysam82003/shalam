<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
/** Local static-site inspection only. No arbitrary HTTP fetch or private-network crawler. */
final class TgSeo
{
    public const PAGES=['index.html','about.html','contact.html','download.html','pro.html','privacy.html','refund.html','terms.html'];
    public static function root(): string { return defined('DIVAN_TEST') && App::cfg('test_site_root') ? (string)App::cfg('test_site_root') : dirname(App::$root); }
    public static function file(string $page): ?string
    {
        if(!in_array($page,array_merge(self::PAGES,['robots.txt','sitemap.xml']),true))return null;
        $root=realpath(self::root());$file=realpath(self::root().'/'.$page);return $root && $file && dirname($file)===$root && is_file($file)?$file:null;
    }
    public static function status(): array
    {
        return ['last_check'=>Settings::get('tg_seo_check'),'pages'=>self::PAGES,'sitemap'=>self::file('sitemap.xml')!==null,'robots'=>self::file('robots.txt')!==null,'search_console'=>['connected'=>false,'url'=>'https://search.google.com/search-console','instructions'=>'مالکیت دامنه را با رکورد DNS تأیید کنید؛ سپس sitemap.xml را ثبت کنید. این نسخه به OAuth سرچ کنسول متصل نیست.'],'scope'=>'بررسی فایل‌های محلی صفحات معرفی؛ شامل لینک‌های راه‌دور و آمار گوگل نیست.'];
    }
    public static function scan(): array
    {
        if(!class_exists('DOMDocument'))throw new ApiError('dom','افزونه PHP DOM لازم است.');
        $rows=[];$hashes=[];$broken=[];$base=parse_url(App::baseUrl());$host=$base['host']??'';$root=realpath(self::root());
        foreach(self::PAGES as $page){$path=self::file($page);if(!$path){$rows[]=['page'=>$page,'exists'=>false];continue;}$html=file_get_contents($path);if(strlen($html)>2000000)continue;
            $dom=new DOMDocument();$prev=libxml_use_internal_errors(true);$dom->loadHTML('<?xml encoding="UTF-8">'.$html,LIBXML_NONET);libxml_clear_errors();libxml_use_internal_errors($prev);$xp=new DOMXPath($dom);
            $title=$xp->evaluate('string(//title)');$desc=$xp->evaluate('string(//meta[translate(@name,"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz")="description"]/@content)');
            $main=$xp->evaluate('string(//main)')?:$xp->evaluate('string(//body)');$hash=hash('sha256',preg_replace('/\s+/u',' ',trim($main)));$hashes[$hash][]=$page;$rows[]=['page'=>$page,'exists'=>true,'title'=>$title,'description'=>$desc];
            foreach($xp->query('//a[@href]|//img[@src]|//script[@src]|//link[@href]') as $node){$url=$node->getAttribute($node->hasAttribute('href')?'href':'src');$p=parse_url($url);if(!$p || (isset($p['host']) && $p['host']!==$host) || (isset($p['scheme']) && !in_array($p['scheme'],['http','https'],true)))continue;$target=rawurldecode($p['path']??'');if($target==='' || str_starts_with($target,'/divan/') || str_starts_with($target,'/install'))continue;$target=ltrim($target,'/');if($target==='')$target='index.html';$resolved=realpath(self::root().'/'.$target);if(!$resolved || !$root || !str_starts_with($resolved,$root.'/'))$broken[]=['page'=>$page,'target'=>mb_substr($url,0,300)];}
        }
        $report=['at'=>Clock::sql(),'rows'=>$rows,'broken'=>array_slice($broken,0,100),'duplicates'=>array_values(array_filter($hashes,fn($v)=>count($v)>1))];Settings::set('tg_seo_check',$report);return $report;
    }
    public static function save(array $d): void
    {
        $page=(string)($d['page']??'');$path=self::file($page);if(!$path || !in_array($page,self::PAGES,true))throw new ApiError('page','صفحه قابل ویرایش پیدا نشد.');
        $title=Management::text($d,'title',100,true);$desc=Management::text($d,'description',300,true);$old=file_get_contents($path);
        if(substr_count(strtolower($old),'<title>')!==1 || !preg_match('/<head\b[^>]*>/i',$old))throw new ApiError('html','ساختار صفحه برای ویرایش ایمن شناخته نشد.');
        $html=preg_replace_callback('#<title>.*?</title>#is',fn()=>'<title>'.e($title).'</title>',$old,1);
        $pattern='#<meta\b(?=[^>]*\bname\s*=\s*["\']description["\'])[^>]*>#i';
        $meta='<meta name="description" content="'.e($desc).'">';
        if(preg_match($pattern,$html))$html=preg_replace_callback($pattern,fn()=>$meta,$html,1);else $html=preg_replace_callback('#</head>#i',fn()=>$meta."\n</head>",$html,1);
        $dir=App::$root.'/storage/tg-site-backups';if(!is_dir($dir)&&!mkdir($dir,0700,true))throw new ApiError('write','پوشه پشتیبان قابل نوشتن نیست.');
        $backup=$dir.'/'.gmdate('Ymd-His').'-'.bin2hex(random_bytes(4)).'-'.$page.'.bak';if(file_put_contents($backup,$old,LOCK_EX)===false)throw new ApiError('write','ذخیره پشتیبان ممکن نشد.');@chmod($backup,0600);
        $temp=tempnam(dirname($path),'.divan-seo-');if($temp===false || file_put_contents($temp,$html,LOCK_EX)===false || !rename($temp,$path)){if($temp && is_file($temp))unlink($temp);throw new ApiError('write','صفحه قابل نوشتن نیست.');}@chmod($path,0644);
    }
}
