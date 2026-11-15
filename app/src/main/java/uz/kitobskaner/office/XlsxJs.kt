package uz.kitobskaner.office

/**
 * Excel jadval muharriri (WebView ichida): formula paneli, formula hisoblagich, katak/diapazon
 * tanlash, formatlash, nusxalash/qo'yish, bekor qilish/qaytarish, ko'rinish filtri.
 *
 * Katak holati atributlarda: data-f (asl formula), data-v (asl qiymat), data-nf (yangi formula;
 * "" — formula olib tashlangan), data-nv (yangi qiymat), data-cv/data-cvt (hisoblangan natija),
 * data-fmt (format o'zgarishlari JSON). [cells] bularni [OfficeEditor] uchun yig'adi.
 * Eslatma: Kotlin raw-string ichida bo'lgani uchun dollar belgisi o'rniga \x24 ishlatiladi.
 */
const val XLSX_JS = """
var XL=(function(){
if(document.body.getAttribute('data-kind')!='xlsx')return null;
var cur=null,anchor=null,rng=null,rangeMode=false,undos=[],redos=[],clip=null,before=null,cache={},stack={};
var NF={gen:'General',int:'#,##0',num2:'#,##0.00',pct:'0.00%',cur:'#,##0.00 "so\'m"',date:'dd.mm.yyyy'};
var fin=document.getElementById('fin'),fref=document.getElementById('fref'),fok=document.getElementById('fok');
function names(){var s=document.querySelectorAll('.sheet'),o={};for(var i=0;i<s.length;i++)o[(s[i].getAttribute('data-name')||'').toLowerCase()]=i;return o}
function ci(L){var n=0;L=L.toUpperCase();for(var i=0;i<L.length;i++)n=n*26+(L.charCodeAt(i)-64);return n-1}
function cn(i){var s='';i++;while(i>0){var m=(i-1)%26;s=String.fromCharCode(65+m)+s;i=Math.floor((i-1)/26)}return s}
function td(si,r,c){return document.querySelector('td[data-c="'+si+'|'+cn(c)+(r+1)+'"]')}
function pos(t){var a=t.getAttribute('data-c').split('|'),m=a[1].match(/^([A-Z]+)(\d+)${'$'}/);return{si:+a[0],r:+m[2]-1,c:ci(m[1])}}
function fOf(t){return t.hasAttribute('data-nf')?t.getAttribute('data-nf'):t.getAttribute('data-f')}
function rawOf(t){if(t.hasAttribute('data-nv'))return t.getAttribute('data-nv');if(t.hasAttribute('data-cv'))return t.getAttribute('data-cv');
 if(t.hasAttribute('data-v'))return t.getAttribute('data-v');return t.innerText}
function num(v){if(typeof v=='number')return v;if(typeof v=='boolean')return v?1:0;var s=String(v).replace(/[\s ]/g,'');if(s==='')return 0;
 if(/^-?\d+(\.\d+)?([eE][-+]?\d+)?$/.test(s))return parseFloat(s);if(/^-?\d+,\d+$/.test(s))return parseFloat(s.replace(',','.'));
 if(/^-?\d+(\.\d+)?%$/.test(s))return parseFloat(s)/100;return NaN}
function isNum(v){return typeof v=='number'||(typeof v=='string'&&v.trim()!==''&&!isNaN(num(v)))}
function N(x){if(x&&x.rng)x=x.vals[0];if(typeof x=='number')return x;if(typeof x=='boolean')return x?1:0;if(x===''||x==null)return 0;
 if(typeof x=='string'&&x.charAt(0)=='#')throw x;var n=num(x);if(isNaN(n))throw '#VALUE!';return n}
function S(x){if(x&&x.rng)x=x.vals[0];if(typeof x=='boolean')return x?'TRUE':'FALSE';if(x==null)return '';return String(x)}
function truthy(x){if(x&&x.rng)x=x.vals[0];if(typeof x=='string'){if(x.toUpperCase()=='TRUE')return true;if(x.toUpperCase()=='FALSE'||x==='')return false;return N(x)!=0}return !!N(x)}
function val(si,r,c){var t=td(si,r,c);if(!t)return '';var key=si+':'+r+':'+c;if(key in cache)return cache[key];var f=fOf(t),v;
 if(f){if(stack[key])throw '#CIRC!';stack[key]=1;try{v=evalF(f,si)}finally{delete stack[key]}}
 else{v=rawOf(t);if(v==='TRUE')v=true;else if(v==='FALSE')v=false;else if(isNum(v)&&(t.hasAttribute('data-v')||t.hasAttribute('data-nv')||t.hasAttribute('data-cv')))v=num(v)}
 cache[key]=v;return v}
function refv(ref,si){var m=ref.match(/^(?:(?:'([^']+)'|([^!]+))!)?(.+)${'$'}/),sh=m[1]||m[2],s2=si;
 if(sh){var nm=names()[sh.toLowerCase()];if(nm===undefined)throw '#REF!';s2=nm}
 var parts=m[3].replace(/\x24/g,'').split(':'),a=parts[0].match(/^([A-Za-z]+)(\d+)${'$'}/);var r0=+a[2]-1,c0=ci(a[1]);
 if(parts.length==1)return val(s2,r0,c0);var b=parts[1].match(/^([A-Za-z]+)(\d+)${'$'}/),r1=+b[2]-1,c1=ci(b[1]),vals=[];
 if(r1<r0){var t=r0;r0=r1;r1=t}if(c1<c0){t=c0;c0=c1;c1=t}if((r1-r0+1)*(c1-c0+1)>200000)throw '#NUM!';
 for(var r=r0;r<=r1;r++)for(var c=c0;c<=c1;c++)vals.push(val(s2,r,c));return{rng:true,vals:vals}}
function tok(s){var out=[],i=0,m;while(i<s.length){var ch=s[i],rest=s.slice(i);
 if(/\s/.test(ch)){i++;continue}
 if(ch=='"'){var j=i+1,str='';while(j<s.length){if(s[j]=='"'){if(s[j+1]=='"'){str+='"';j+=2;continue}break}str+=s[j++]}out.push({t:'s',v:str});i=j+1;continue}
 m=rest.match(/^((?:'[^']+'|[A-Za-z_][\w.]*)!)?\x24?[A-Za-z]{1,3}\x24?\d+(?::\x24?[A-Za-z]{1,3}\x24?\d+)?(?![\w(])/);if(m){out.push({t:'r',v:m[0]});i+=m[0].length;continue}
 m=rest.match(/^\d+(\.\d+)?([eE][-+]?\d+)?/);if(m){out.push({t:'n',v:parseFloat(m[0])});i+=m[0].length;continue}
 m=rest.match(/^[A-Za-z_][\w.]*/);if(m){out.push({t:'w',v:m[0].toUpperCase()});i+=m[0].length;continue}
 m=rest.match(/^(<>|<=|>=|[-+*\/^&=<>(),;%])/);if(m){out.push({t:'o',v:m[0]});i+=m[0].length;continue}
 throw '#PARSE'}return out}
function compare(a,b,o){if(a&&a.rng)a=a.vals[0];if(b&&b.rng)b=b.vals[0];var x=a,y=b;
 if(typeof a=='string'&&typeof b=='string'){x=a.toLowerCase();y=b.toLowerCase()}else if(isNum(a)&&isNum(b)){x=N(a);y=N(b)}
 switch(o){case'=':return x==y;case'<>':return x!=y;case'<':return x<y;case'>':return x>y;case'<=':return x<=y;default:return x>=y}}
function flat(args){var o=[];for(var i=0;i<args.length;i++){var a=args[i];if(a&&a.rng){for(var j=0;j<a.vals.length;j++)o.push({v:a.vals[j],ref:1})}else o.push({v:a,ref:0})}return o}
function nums(args){var o=[],f=flat(args);for(var i=0;i<f.length;i++){var x=f[i];if(x.ref){if(typeof x.v=='number')o.push(x.v);else if(typeof x.v=='string'&&x.v.charAt(0)=='#')throw x.v}else o.push(N(x.v))}return o}
function crit(c){var s=S(c),m=s.match(/^(<>|<=|>=|=|<|>)?(.*)${'$'}/),op=m[1]||'=',rhs=m[2];
 return function(v){if(isNum(rhs)&&isNum(v))return compare(N(v),N(rhs),op);return compare(S(v),rhs,op)}}
function fn(n,a){var x,i,f;switch(n){
 case'SUM':return nums(a).reduce(function(s,v){return s+v},0);
 case'AVERAGE':x=nums(a);if(!x.length)throw '#DIV/0!';return x.reduce(function(s,v){return s+v},0)/x.length;
 case'MIN':x=nums(a);return x.length?Math.min.apply(null,x):0;
 case'MAX':x=nums(a);return x.length?Math.max.apply(null,x):0;
 case'COUNT':return flat(a).filter(function(o){return typeof o.v=='number'||(!o.ref&&isNum(o.v))}).length;
 case'COUNTA':return flat(a).filter(function(o){return o.v!==''&&o.v!=null}).length;
 case'COUNTBLANK':return flat(a).filter(function(o){return o.v===''||o.v==null}).length;
 case'IF':return truthy(a[0])?(a.length>1?a[1]:true):(a.length>2?a[2]:false);
 case'IFERROR':return a[0];
 case'AND':return flat(a).every(function(o){return truthy(o.v)});
 case'OR':return flat(a).some(function(o){return truthy(o.v)});
 case'NOT':return !truthy(a[0]);
 case'ROUND':x=Math.pow(10,a.length>1?N(a[1]):0);return Math.round(N(a[0])*x)/x;
 case'ROUNDUP':x=Math.pow(10,a.length>1?N(a[1]):0);return (N(a[0])<0?-1:1)*Math.ceil(Math.abs(N(a[0]))*x)/x;
 case'ROUNDDOWN':x=Math.pow(10,a.length>1?N(a[1]):0);return (N(a[0])<0?-1:1)*Math.floor(Math.abs(N(a[0]))*x)/x;
 case'ABS':return Math.abs(N(a[0]));case'SQRT':x=N(a[0]);if(x<0)throw '#NUM!';return Math.sqrt(x);
 case'INT':return Math.floor(N(a[0]));case'MOD':x=N(a[1]);if(x==0)throw '#DIV/0!';return N(a[0])-x*Math.floor(N(a[0])/x);
 case'POWER':return Math.pow(N(a[0]),N(a[1]));case'PI':return Math.PI;
 case'CONCATENATE':case'CONCAT':return flat(a).map(function(o){return S(o.v)}).join('');
 case'LEN':return S(a[0]).length;case'UPPER':return S(a[0]).toUpperCase();case'LOWER':return S(a[0]).toLowerCase();
 case'TRIM':return S(a[0]).trim().replace(/\s+/g,' ');
 case'LEFT':return S(a[0]).substring(0,a.length>1?N(a[1]):1);
 case'RIGHT':x=S(a[0]);return x.substring(x.length-(a.length>1?N(a[1]):1));
 case'MID':return S(a[0]).substr(N(a[1])-1,N(a[2]));
 case'SUMIF':f=crit(a[1]);x=a[0].rng?a[0].vals:[a[0]];var sv=a.length>2&&a[2].rng?a[2].vals:x,s2=0;
  for(i=0;i<x.length;i++)if(f(x[i])&&isNum(sv[i]))s2+=N(sv[i]);return s2;
 case'COUNTIF':f=crit(a[1]);x=a[0].rng?a[0].vals:[a[0]];return x.filter(function(v){return v!==''&&f(v)}).length;
 case'AVERAGEIF':f=crit(a[1]);x=a[0].rng?a[0].vals:[a[0]];var av=a.length>2&&a[2].rng?a[2].vals:x,t=0,k=0;
  for(i=0;i<x.length;i++)if(f(x[i])&&isNum(av[i])){t+=N(av[i]);k++}if(!k)throw '#DIV/0!';return t/k;
 default:throw 'UNSUP'}}
function evalF(f,si){var T=tok(f),p=0;
 function eat(v){var t=T[p];if(t&&t.t=='o'&&t.v==v){p++;return true}return false}
 function isOp(re){return T[p]&&T[p].t=='o'&&re.test(T[p].v)}
 function cmp(){var a=cat();while(isOp(/^(=|<>|<=|>=|<|>)${'$'}/)){var o=T[p++].v;a=compare(a,cat(),o)}return a}
 function cat(){var a=add();while(eat('&'))a=S(a)+S(add());return a}
 function add(){var a=mul();while(isOp(/^[-+]${'$'}/)){var o=T[p++].v,b=mul();a=o=='+'?N(a)+N(b):N(a)-N(b)}return a}
 function mul(){var a=pw();while(isOp(/^[*\/]${'$'}/)){var o=T[p++].v,b=pw();if(o=='/'){if(N(b)==0)throw '#DIV/0!';a=N(a)/N(b)}else a=N(a)*N(b)}return a}
 function pw(){var a=un();while(eat('^'))a=Math.pow(N(a),N(un()));return a}
 function un(){if(eat('-'))return -N(un());if(eat('+'))return N(un());return post()}
 function post(){var a=prim();while(eat('%'))a=N(a)/100;return a}
 function prim(){var t=T[p++];if(!t)throw '#PARSE';if(t.t=='n'||t.t=='s')return t.v;if(t.t=='r')return refv(t.v,si);
  if(t.t=='o'&&t.v=='('){var v=cmp();if(!eat(')'))throw '#PARSE';return v}
  if(t.t=='w'){if(t.v=='TRUE')return true;if(t.v=='FALSE')return false;
   if(eat('(')){var args=[];if(!eat(')')){do{if(T[p]&&T[p].t=='o'&&(T[p].v==','||T[p].v==';'||T[p].v==')'))args.push('');else
     if(t.v=='IFERROR'&&args.length==0){try{args.push(cmp())}catch(e){if(e=='UNSUP'||e=='#PARSE')throw e;args.push({err:e})}}else args.push(cmp())}while(eat(',')||eat(';'));
    if(!eat(')'))throw '#PARSE'}
    if(t.v=='IFERROR')return args[0]&&args[0].err?(args.length>1?args[1]:''):args[0];return fn(t.v,args)}
   throw '#NAME?'}
  throw '#PARSE'}
 var v=cmp();if(p<T.length)throw '#PARSE';if(v&&v.rng)v=v.vals[0];return v}
function grp(n,dec){var s=Math.abs(n).toFixed(dec),parts=s.split('.');parts[0]=parts[0].replace(/\B(?=(\d{3})+(?!\d))/g,' ');return (n<0?'-':'')+parts.join('.')}
function fmtNum(v,code){if(!code||code=='General'){if(Number.isInteger(v))return String(v);return String(parseFloat(v.toPrecision(10)))}
 var c=code.replace(/"[^"]*"/g,'');
 if(/[dy]/i.test(c)&&!/0/.test(c)){var d=new Date(Date.UTC(1899,11,30)+Math.round(v*86400000));
  function z(x){return x<10?'0'+x:x}return z(d.getUTCDate())+'.'+z(d.getUTCMonth()+1)+'.'+d.getUTCFullYear()}
 var dec=(c.split('.')[1]||'').replace(/[^0#]/g,'').replace(/#/g,'').length;
 if(c.indexOf('%')>=0)return grp(v*100,dec).replace(/ /g,'')+'%';
 var s=c.indexOf(',')>=0?grp(v,dec):Math.abs(v).toFixed(dec)*(v<0?-1:1)+'';if(c.indexOf(',')<0)s=v.toFixed(dec);
 var lit=code.match(/"([^"]*)"/);return lit?s+' '+lit[1]:s}
function codeOf(t){var fm=t.getAttribute('data-fmt');if(fm){try{var o=JSON.parse(fm);if(o.nf)return NF[o.nf]}catch(e){}}return t.getAttribute('data-nfmt')}
function show(t,v){if(typeof v=='number')return fmtNum(v,codeOf(t));if(typeof v=='boolean')return v?'TRUE':'FALSE';return v==null?'':String(v)}
function recalc(){cache={};var ts=document.querySelectorAll('td[data-f],td[data-nf]');for(var i=0;i<ts.length;i++){var t=ts[i],f=fOf(t);if(!f)continue;
 var p=pos(t),v;try{v=val(p.si,p.r,p.c)}catch(e){if(e=='UNSUP'||e=='#PARSE'){if(t.hasAttribute('data-nf')){t.removeAttribute('data-cv');if(t!==document.activeElement)t.innerText='='+f}continue}v=String(e)}
 t.setAttribute('data-cv',typeof v=='boolean'?(v?'1':'0'):String(v));t.setAttribute('data-cvt',typeof v=='number'?'n':typeof v=='boolean'?'b':(String(v).charAt(0)=='#'?'e':'s'));
 if(t!==document.activeElement)t.innerText=show(t,v)}}
function snap(t){var a={};['data-nf','data-nv','data-fmt','data-cv','data-cvt','style'].forEach(function(k){a[k]=t.getAttribute(k)});return{t:t,html:t.innerHTML,a:a}}
function restore(s){s.t.innerHTML=s.html;for(var k in s.a){if(s.a[k]==null)s.t.removeAttribute(k);else s.t.setAttribute(k,s.a[k])}}
function record(ts){undos.push(ts.map(snap));if(undos.length>200)undos.shift();redos=[]}
function undo(){var u=undos.pop();if(!u)return;redos.push(u.map(function(s){return snap(s.t)}));u.forEach(restore);recalc();sync()}
function redo(){var u=redos.pop();if(!u)return;undos.push(u.map(function(s){return snap(s.t)}));u.forEach(restore);recalc();sync()}
function seen(t){if(before&&before.t===t)before.shown=t.innerText}
function setText(t,text){text=text==null?'':String(text);if(text.charAt(0)=='='&&text.length>1){t.setAttribute('data-nf',text.substring(1).trim());t.removeAttribute('data-nv')}
 else{t.setAttribute('data-nf','');t.setAttribute('data-nv',text);t.removeAttribute('data-cv');t.removeAttribute('data-cvt');
  t.innerText=isNum(text)&&codeOf(t)?fmtNum(num(text),codeOf(t)):text}seen(t)}
function commit(t,text,noRec){if(!t)return;if(!noRec)record([t]);setText(t,text);recalc();sync()}
function sel(){if(rng){var o=[];for(var r=rng.r0;r<=rng.r1;r++)for(var c=rng.c0;c<=rng.c1;c++){var t=td(rng.si,r,c);if(t)o.push(t)}return o}return cur?[cur]:[]}
function paint(){var x=document.querySelectorAll('td.crng,td.csel');for(var i=0;i<x.length;i++)x[i].classList.remove('crng','csel');
 if(cur)cur.classList.add('csel');if(rng)sel().forEach(function(t){t.classList.add('crng')})}
function rngRef(){if(!rng)return cur?cur.getAttribute('data-c').split('|')[1]:'';return cn(rng.c0)+(rng.r0+1)+':'+cn(rng.c1)+(rng.r1+1)}
function sync(){if(!cur){fref.textContent='';fin.value='';return}var f=fOf(cur);fref.textContent=rngRef();
 if(document.activeElement!==fin)fin.value=f?'='+f:(cur.hasAttribute('data-nv')?cur.getAttribute('data-nv'):cur.innerText)}
function pick(t){cur=t;var p=pos(t);
 if(rangeMode){if(!anchor||anchor.si!=p.si)anchor=p;rng={si:p.si,r0:Math.min(anchor.r,p.r),c0:Math.min(anchor.c,p.c),r1:Math.max(anchor.r,p.r),c1:Math.max(anchor.c,p.c)}}
 else{anchor=p;rng=null}paint();sync()}
document.addEventListener('focusin',function(e){var t=e.target.closest&&e.target.closest('td[data-c]');if(!t||rangeMode)return;pick(t);
 before={t:t,text:t.innerText,s:snap(t)};var f=fOf(t);if(f)t.innerText='='+f;before.shown=t.innerText})
document.addEventListener('focusout',function(e){var t=e.target.closest&&e.target.closest('td[data-c]');if(!t||!before||before.t!==t)return;var b=before;before=null;
 var txt=t.innerText.replace(/\u00a0/g,' ').trim();
 if(txt==b.shown.replace(/\u00a0/g,' ').trim()){if(fOf(t))recalc();return}
 undos.push([b.s]);redos=[];setText(t,txt);recalc();sync()})
document.addEventListener('click',function(e){var t=e.target.closest&&e.target.closest('td[data-c]');if(!t)return;if(rangeMode){pick(t);e.preventDefault()}})
document.addEventListener('keydown',function(e){if(e.key=='Enter'&&e.target.closest&&e.target.closest('td[data-c]')){e.preventDefault();var t=e.target.closest('td[data-c]'),p=pos(t),n=td(p.si,p.r+1,p.c);t.blur();if(n)n.focus()}})
function fromBar(){if(!cur)return;var t=cur;commit(t,fin.value);var p=pos(t),n=td(p.si,p.r+1,p.c);if(n){pick(n);if(!rangeMode)n.focus()}}
if(fin){fin.addEventListener('keydown',function(e){if(e.key=='Enter'){e.preventDefault();fromBar()}});fok.addEventListener('click',fromBar)}
function range(on){rangeMode=on;anchor=null;if(on)rng=null;var ts=document.querySelectorAll('td[data-c]');var ed=document.body.classList.contains('editing');
 for(var i=0;i<ts.length;i++)ts[i].contentEditable=(ed&&!on)?'true':'false';if(on&&document.activeElement)document.activeElement.blur();if(!on){rng=null;paint();sync()}}
function applyFmt(t,o){var fm={};try{fm=JSON.parse(t.getAttribute('data-fmt')||'{}')}catch(e){}for(var k in o)fm[k]=o[k];t.setAttribute('data-fmt',JSON.stringify(fm));
 var st=t.style;if('b' in fm)st.fontWeight=fm.b?'bold':'normal';if('i' in fm)st.fontStyle=fm.i?'italic':'normal';if(fm.c)st.color=fm.c;
 if(fm.bg)st.background=fm.bg=='none'?'#fff':fm.bg;if(fm.al)st.textAlign=fm.al;if('bd' in fm)st.border=fm.bd?'1px solid #444':'';
 if(fm.nf){var r=rawOf(t);if(isNum(r))t.innerText=fmtNum(num(r),NF[fm.nf])}seen(t)}
function fmt(o){var ts=sel();if(!ts.length)return false;record(ts);ts.forEach(function(t){applyFmt(t,o)});return true}
function toggle(k){var ts=sel();if(!ts.length)return false;var fm={};try{fm=JSON.parse(ts[0].getAttribute('data-fmt')||'{}')}catch(e){}
 var on=k in fm?!fm[k]:!(k=='b'?parseInt(getComputedStyle(ts[0]).fontWeight)>=600:getComputedStyle(ts[0]).fontStyle=='italic');var o={};o[k]=on;return fmt(o)}
function clear(){var ts=sel();if(!ts.length)return;record(ts);ts.forEach(function(t){setText(t,'')});recalc();sync()}
function translate(f,dr,dc){return f.replace(/("(?:[^"]|"")*")|((?:'[^']+'|[A-Za-z_][\w.]*)!)?(\x24?)([A-Za-z]{1,3})(\x24?)(\d+)(?![\w(])/g,
 function(m,str,pre,ca,col,ra,row,off,all){if(str)return m;var prev=all.charAt(off-1);if(off>0&&/[\w.]/.test(prev))return m;
  var c2=ca?col:cn(Math.max(0,ci(col)+dc)),r2=ra?row:Math.max(1,+row+dr);return (pre||'')+ca+c2+ra+r2})}
function copy(){var ts=sel();if(!ts.length)return 0;var p0=pos(ts[0]),items=[];
 ts.forEach(function(t){var p=pos(t),f=fOf(t);items.push({dr:p.r-p0.r,dc:p.c-p0.c,f:f||null,v:f?null:(t.hasAttribute('data-nv')?t.getAttribute('data-nv'):(t.hasAttribute('data-v')?t.getAttribute('data-v'):t.innerText)),fmt:t.getAttribute('data-fmt')})});
 clip={r:p0.r,c:p0.c,items:items};return items.length}
function paste(){if(!clip||!cur)return 0;var p=pos(cur),ts=[],todo=[];clip.items.forEach(function(it){var t=td(p.si,p.r+it.dr,p.c+it.dc);if(t){ts.push(t);todo.push([t,it])}});
 record(ts);todo.forEach(function(x){var t=x[0],it=x[1];setText(t,it.f?'='+translate(it.f,p.r-clip.r,p.c-clip.c):it.v);if(it.fmt){try{applyFmt(t,JSON.parse(it.fmt))}catch(e){}}});recalc();sync();return todo.length}
function autoFn(name){var ts=sel();if(rng&&ts.length>1){var target=td(rng.si,rng.r1+1,rng.c0);if(rng.c1>rng.c0&&rng.r1==rng.r0)target=td(rng.si,rng.r0,rng.c1+1);
  if(target){var ref=cn(rng.c0)+(rng.r0+1)+':'+cn(rng.c1)+(rng.r1+1);commit(target,'='+name+'('+ref+')');range(false);pick(target);return true}}
 if(cur){fin.value='='+name+'(';fin.focus();return true}return false}
function curSheet(){var s=document.querySelectorAll('.sheet');for(var i=0;i<s.length;i++)if(s[i].style.display!='none')return i;return 0}
function selInfo(){if(rng)return JSON.stringify(rng);if(cur){var p=pos(cur);return JSON.stringify({si:p.si,r0:p.r,c0:p.c,r1:p.r,c1:p.c})}
 return JSON.stringify({si:curSheet(),r0:-1,c0:-1,r1:-1,c1:-1})}
function filter(col,text){var sh=document.querySelectorAll('.sheet')[curSheet()],rows=sh.querySelectorAll('table.x tr'),n=0;text=(text||'').toLowerCase();
 for(var i=2;i<rows.length;i++){var c=rows[i].cells[col+1],ok=!text||(c&&c.innerText.toLowerCase().indexOf(text)>=0);rows[i].style.display=ok?'':'none';if(ok&&text)n++}return n}
function differs(t){var a=t.getAttribute('data-cv'),b=t.getAttribute('data-v');if(b==null)return true;if(isNum(a)&&isNum(b))return Math.abs(num(a)-num(b))>1e-9*Math.max(1,Math.abs(num(b)));return a!=b}
function cells(){var out=[],ts=document.querySelectorAll('td[data-c]');for(var i=0;i<ts.length;i++){var t=ts[i],o=null,c=t.getAttribute('data-c');
 if(t.hasAttribute('data-nf')){var nf=t.getAttribute('data-nf');o={c:c};if(nf){o.f=nf;o.v=t.getAttribute('data-cv')||'';o.vt=t.hasAttribute('data-cv')?(t.getAttribute('data-cvt')||'s'):'x'}
  else o.v=t.getAttribute('data-nv')||''}
 else if(t.hasAttribute('data-cv')&&(t.hasAttribute('data-f')||t.hasAttribute('data-sf'))&&differs(t)){o={c:c,keepf:1,v:t.getAttribute('data-cv'),vt:t.getAttribute('data-cvt')||'s'}}
 if(t.hasAttribute('data-fmt')){o=o||{c:c};try{o.fmt=JSON.parse(t.getAttribute('data-fmt'))}catch(e){}}
 if(o)out.push(o)}return out}
function touched(t){var f=fOf(t);record([t]);setText(t,t.innerText);recalc()}
return{cells:cells,undo:undo,redo:redo,commit:function(text){commit(cur,text)},range:range,fmt:function(o){return fmt(o)},toggle:toggle,clear:clear,copy:copy,paste:paste,
 autoFn:autoFn,selInfo:selInfo,curSheet:curSheet,filter:filter,touched:touched,recalc:recalc,translate:translate,evalF:evalF}
})();
"""
